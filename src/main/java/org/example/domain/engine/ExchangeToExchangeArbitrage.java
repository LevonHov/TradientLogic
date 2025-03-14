package org.example.domain.engine;

import org.example.data.model.ArbitrageOpportunity;
import org.example.data.model.RiskAssessment;
import org.example.data.model.Ticker;
import org.example.data.model.TradingPair;
import org.example.data.model.fee.Fee;
import org.example.data.model.fee.FeeCalculator;
import org.example.data.service.ExchangeService;
import org.example.domain.risk.RiskCalculator;

import java.util.Date;

/**
 * This class encapsulates the logic for detecting an arbitrage opportunity
 * between two exchanges for a given trading pair, taking fees into account.
 * It now outputs detailed logging at each computation step.
 *
 * All log messages are accumulated internally and can be retrieved via getLogMessages().
 */
public class ExchangeToExchangeArbitrage {
    private ExchangeService exchangeA;
    private ExchangeService exchangeB;
    private RiskCalculator riskCalculator;
    private double minProfitPercent = 0.1; // Default minimum profit percentage (0.1%)
    private StringBuilder logBuilder = new StringBuilder();

    /**
     * Constructor that initializes the two exchange services.
     *
     * @param exchangeA First exchange service instance.
     * @param exchangeB Second exchange service instance.
     */
    public ExchangeToExchangeArbitrage(ExchangeService exchangeA, ExchangeService exchangeB) {
        this.exchangeA = exchangeA;
        this.exchangeB = exchangeB;
        // Convert minProfitPercent to decimal for the risk calculator calculation.
        this.riskCalculator = new RiskCalculator(minProfitPercent / 100);
    }

    /**
     * Returns all accumulated log messages.
     *
     * @return A string containing the log messages.
     */
    public String getLogMessages() {
        return logBuilder.toString();
    }

    /**
     * Calculates the potential arbitrage opportunity between two exchanges for a given trading pair.
     *
     * @param pair The trading pair to analyze.
     * @return An ArbitrageOpportunity object if an opportunity exists, null otherwise.
     */
    public ArbitrageOpportunity calculateArbitrage(TradingPair pair) {
        if (pair == null) {
            logBuilder.append("Trading pair is null; aborting arbitrage calculation.\n");
            return null;
        }

        String symbol = pair.getSymbol();
        logBuilder.append("Analyzing arbitrage for symbol: ").append(symbol).append("\n");

        // Get ticker data from both exchanges
        Ticker tickerA = exchangeA.getTickerData(symbol);
        Ticker tickerB = exchangeB.getTickerData(symbol);

        if (tickerA == null || tickerB == null) {
            logBuilder.append("Insufficient ticker data from one or both exchanges.\n");
            return null;  // Can't compare if we don't have data from both exchanges
        }

        // Get the fee structures for each exchange (use taker fees as we'll be executing market orders)
        Fee feeA = exchangeA.getTakerFee();
        Fee feeB = exchangeB.getTakerFee();

        logBuilder.append("Comparing ").append(exchangeA.getExchangeName())
                .append(" (fees: ").append(feeA.getDescription()).append(") vs ")
                .append(exchangeB.getExchangeName()).append(" (fees: ")
                .append(feeB.getDescription()).append(")\n");

        // Determine appropriate quantity based on token price
        double buyOnAPrice = tickerA.getAskPrice();
        double quantity;
        if (buyOnAPrice < 0.001) {
            quantity = 1000000; // 1 million units for micro-priced tokens like SHIB
            logBuilder.append("Low-value token detected. Using 1,000,000 units for calculations.\n");
        } else if (buyOnAPrice < 1.0) {
            quantity = 1000; // 1,000 units for tokens under $1
            logBuilder.append("Medium-value token detected. Using 1,000 units for calculations.\n");
        } else if (buyOnAPrice < 100.0) {
            quantity = 10; // 10 units for tokens under $100
            logBuilder.append("High-value token detected. Using 10 units for calculations.\n");
        } else {
            quantity = 0.01; // 0.01 units for expensive tokens like BTC
            logBuilder.append("Very high-value token detected. Using 0.01 units for calculations.\n");
        }

        // Case 1: Buy on A, sell on B
        double sellOnBPrice = tickerB.getBidPrice();

        // Calculate net profit after fees for case 1 using the FeeCalculator
        double profitAB = FeeCalculator.calculateArbitrageProfit(buyOnAPrice, sellOnBPrice, quantity, feeA, feeB);
        double profitPercentAB = FeeCalculator.calculateArbitrageProfitPercentage(buyOnAPrice, sellOnBPrice, quantity, feeA, feeB);

        // Calculate fee percentages for better visibility
        double buyFeePercentA = feeA.calculateFee(buyOnAPrice * quantity) / (buyOnAPrice * quantity) * 100;
        double sellFeePercentB = feeB.calculateFee(sellOnBPrice * quantity) / (sellOnBPrice * quantity) * 100;

        logBuilder.append("Buy on ").append(exchangeA.getExchangeName()).append(" at ").append(formatPrice(buyOnAPrice))
                .append(", Sell on ").append(exchangeB.getExchangeName()).append(" at ").append(formatPrice(sellOnBPrice))
                .append(" = ").append(String.format("%.4f", profitPercentAB))
                .append("% profit after fees (buy fee: ").append(String.format("%.4f", buyFeePercentA))
                .append("%, sell fee: ").append(String.format("%.4f", sellFeePercentB)).append("%)\n");

        // Case 2: Buy on B, sell on A
        double buyOnBPrice = tickerB.getAskPrice();
        double sellOnAPrice = tickerA.getBidPrice();

        // Calculate net profit after fees for case 2 using the FeeCalculator
        double profitBA = FeeCalculator.calculateArbitrageProfit(buyOnBPrice, sellOnAPrice, quantity, feeB, feeA);
        double profitPercentBA = FeeCalculator.calculateArbitrageProfitPercentage(buyOnBPrice, sellOnAPrice, quantity, feeB, feeA);

        // Calculate fee percentages for better visibility
        double buyFeePercentB = feeB.calculateFee(buyOnBPrice * quantity) / (buyOnBPrice * quantity) * 100;
        double sellFeePercentA = feeA.calculateFee(sellOnAPrice * quantity) / (sellOnAPrice * quantity) * 100;

        logBuilder.append("Buy on ").append(exchangeB.getExchangeName()).append(" at ").append(formatPrice(buyOnBPrice))
                .append(", Sell on ").append(exchangeA.getExchangeName()).append(" at ").append(formatPrice(sellOnAPrice))
                .append(" = ").append(String.format("%.4f", profitPercentBA))
                .append("% profit after fees (buy fee: ").append(String.format("%.4f", buyFeePercentB))
                .append("%, sell fee: ").append(String.format("%.4f", sellFeePercentA)).append("%)\n");

        // Determine which direction has the higher profit potential and meets the minimum profit threshold
        if (profitPercentAB > profitPercentBA && profitPercentAB > minProfitPercent) {
            // Calculate the raw price difference percentage
            double priceDiffPercent = ((sellOnBPrice / buyOnAPrice) - 1) * 100;
            
            // Calculate net profit percentage (price difference minus fees)
            double netProfitPercent = priceDiffPercent - buyFeePercentA - sellFeePercentB;
            
            // Risk assessment is calculated here
            RiskAssessment riskAssessment = riskCalculator.calculateRisk(tickerA, tickerB, 
                    buyFeePercentA / 100, sellFeePercentB / 100); // Convert percentages to decimals
            
            logBuilder.append("Arbitrage opportunity detected: Buy on ").append(exchangeA.getExchangeName())
                    .append(", sell on ").append(exchangeB.getExchangeName())
                    .append(" (").append(String.format("%.4f", netProfitPercent)).append("% net profit)\n");
            
            // Track fees for this potential trade
            exchangeA.calculateAndTrackFee(symbol, buyOnAPrice * quantity, false); // taker order (market buy)
            exchangeB.calculateAndTrackFee(symbol, sellOnBPrice * quantity, false); // taker order (market sell)
            
            ArbitrageOpportunity opportunity = new ArbitrageOpportunity(
                    pair.getSymbol(),           // Normalized symbol
                    symbol,                     // Buy symbol
                    symbol,                     // Sell symbol
                    exchangeA.getExchangeName(),// Buy exchange
                    exchangeB.getExchangeName(),// Sell exchange
                    buyOnAPrice,                // Buy price
                    sellOnBPrice,               // Sell price
                    netProfitPercent            // Net profit percentage after fees
            );
            opportunity.setRiskAssessment(riskAssessment);
            return opportunity;

        } else if (profitPercentBA > minProfitPercent) {
            // Calculate the raw price difference percentage
            double priceDiffPercent = ((sellOnAPrice / buyOnBPrice) - 1) * 100;
            
            // Calculate net profit percentage (price difference minus fees)
            double netProfitPercent = priceDiffPercent - buyFeePercentB - sellFeePercentA;
            
            RiskAssessment riskAssessment = riskCalculator.calculateRisk(tickerB, tickerA, 
                    buyFeePercentB / 100, sellFeePercentA / 100); // Convert percentages to decimals
            
            logBuilder.append("Arbitrage opportunity detected: Buy on ").append(exchangeB.getExchangeName())
                    .append(", sell on ").append(exchangeA.getExchangeName())
                    .append(" (").append(String.format("%.4f", netProfitPercent)).append("% net profit)\n");
            
            // Track fees for this potential trade
            exchangeB.calculateAndTrackFee(symbol, buyOnBPrice * quantity, false); // taker order (market buy)
            exchangeA.calculateAndTrackFee(symbol, sellOnAPrice * quantity, false); // taker order (market sell)
            
            ArbitrageOpportunity opportunity = new ArbitrageOpportunity(
                    pair.getSymbol(),            // Normalized symbol
                    symbol,                      // Buy symbol
                    symbol,                      // Sell symbol
                    exchangeB.getExchangeName(), // Buy exchange
                    exchangeA.getExchangeName(), // Sell exchange
                    buyOnBPrice,                 // Buy price
                    sellOnAPrice,                // Sell price
                    netProfitPercent             // Net profit percentage after fees
            );
            opportunity.setRiskAssessment(riskAssessment);
            return opportunity;
        }

        logBuilder.append("No profitable arbitrage opportunity found for symbol: ").append(symbol).append("\n");
        // No profitable arbitrage opportunity found
        return null;
    }

    /**
     * Formats price values with appropriate scientific notation for small values
     * 
     * @param price The price to format
     * @return A formatted string representation of the price
     */
    private String formatPrice(double price) {
        if (price < 0.001) {
            return String.format("%.4E", price);
        } else {
            return String.format("%.8f", price);
        }
    }
    
    /**
     * Utility method to format ticker information for printing.
     *
     * @param ticker The Ticker object.
     * @return A string representation of the ticker data.
     */
    private String tickerToString(Ticker ticker) {
        if (ticker == null) return "null";
        return "[Ask Price: " + formatPrice(ticker.getAskPrice()) + ", Bid Price: " + formatPrice(ticker.getBidPrice()) + "]";
    }
}