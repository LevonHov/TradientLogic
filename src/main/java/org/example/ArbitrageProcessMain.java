package org.example;

import org.example.data.model.ArbitrageOpportunity;
import org.example.data.model.Ticker;
import org.example.data.model.TradingPair;
import org.example.data.model.RiskAssessment;
import org.example.data.model.fee.ExchangeFeeFactory;
import org.example.data.model.fee.FeeReportGenerator;
import org.example.data.model.fee.Fee;
import org.example.data.model.fee.FeeCalculator;
import org.example.data.service.BinanceExchangeService;
import org.example.data.service.BybitV5ExchangeService;
import org.example.data.service.CoinbaseExchangeService;
import org.example.data.service.ExchangeService;
import org.example.data.service.KrakenExchangeService;
import org.example.domain.engine.ArbitrageEngine;
import org.example.domain.engine.ExchangeToExchangeArbitrage;
import org.example.domain.risk.RiskCalculator;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ArbitrageProcessMain {
    // Minimum profit percentage to consider an arbitrage opportunity
    private static final double MIN_PROFIT_PERCENT = 0.1; // 0.1%
    
    // Store exchange symbol mappings
    private static Map<ExchangeService, Map<String, String>> exchangeSymbolMap = new HashMap<>();
    
    // Flag to enable fee reporting
    private static final boolean ENABLE_FEE_REPORTS = true;

    public static void main(String[] args) {
        System.out.println("=== Starting Real-time Arbitrage Process with WebSocket Data ===");

        // Step 1: Initialize all Exchange Services with advanced fee configurations
        System.out.println("\n[Step 1] Initializing Exchange Services...");
        
        // Initialize with default fees, but we'll update with volume-based tiers
        BinanceExchangeService binance = new BinanceExchangeService(0.001);  // Initial fee: 0.1%
        CoinbaseExchangeService coinbase = new CoinbaseExchangeService(0.006); // Initial fee: 0.6%
        KrakenExchangeService kraken = new KrakenExchangeService(0.0026);    // Initial fee: 0.26%
        BybitV5ExchangeService bybit = new BybitV5ExchangeService(0.001);    // Initial fee: 0.1%
        
        // Configure exchange-specific fee structures based on trading volume
        binance.updateFeesTiers(5_000_000.0);
        binance.setBnbDiscount(true);
        coinbase.updateFeesTiers(100_000.0);
        kraken.updateFeesTiers(250_000.0);
        bybit.updateFeesTiers(0.0);

        List<ExchangeService> exchanges = new ArrayList<>();
        exchanges.add(binance);
        exchanges.add(coinbase);
        exchanges.add(kraken);
        exchanges.add(bybit);

        // Step 2: Fetch Trading Pairs from Each Exchange using REST API.
        System.out.println("\n[Step 2] Fetching Trading Pairs from each Exchange...");
        for (ExchangeService ex : exchanges) {
            System.out.println("\n[" + ex.getExchangeName() + "] Fetching Trading Pairs...");
            List<TradingPair> pairs = ex.fetchTradingPairs();
            System.out.println("[" + ex.getExchangeName() + "] Fetched " + pairs.size() + " trading pairs.");
        }

        // Step 3: Determine common trading pairs among all exchanges using symbol normalization
        System.out.println("\n[Step 3] Determining common trading pairs using symbol normalization...");
        List<String> commonSymbols = findCommonSymbols(exchanges);
        
        // For demo purposes, limit to a few common symbols if there are too many
        if (commonSymbols.size() > 10) {
            commonSymbols = commonSymbols.subList(0, 10);
            System.out.println("Limited to 10 common symbols for demonstration purposes");
        }
        
        // Step 4: Initialize WebSocket connections for all exchanges with proper error handling
        if (!commonSymbols.isEmpty()) {
            try {
                for (ExchangeService ex : exchanges) {
                    try {
                        List<String> exchangeSpecificSymbols = new ArrayList<>();
                        Map<String, String> symbolMap = exchangeSymbolMap.get(ex);
                        
                        if (symbolMap != null) {
                            for (String normalizedSymbol : commonSymbols) {
                                String exchangeSymbol = symbolMap.get(normalizedSymbol);
                                if (exchangeSymbol != null) {
                                    exchangeSpecificSymbols.add(exchangeSymbol);
                                }
                            }
                        }
                        
                        if (exchangeSpecificSymbols.isEmpty()) {
                            continue;
                        }
                        
                        boolean success = ex.initializeWebSocket(exchangeSpecificSymbols);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Step 5: Run the real-time arbitrage process
        System.out.println("\n[Step 5] Starting Real-time Arbitrage Monitoring...");
        System.out.println("Press Ctrl+C to stop the process.\n");

        // Convert the list to a set for the runDirectArbitrageComparison method
        Set<String> commonSymbolsSet = new HashSet<>(commonSymbols);

        if (commonSymbolsSet.isEmpty()) {
            System.out.println("No common symbols found. Ending arbitrage process.");
            return;
        }

        // Run initial comparison
        try {
            runDirectArbitrageComparison(exchanges, commonSymbolsSet);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Schedule periodic arbitrage scan
        schedulePeriodicScans(exchanges, commonSymbolsSet);
    }
    
    /**
     * Prints fee reports for all exchanges.
     * 
     * @param exchanges List of exchange services
     */
    private static void printFeeReports(List<ExchangeService> exchanges) {
        // Print consolidated fee report
        System.out.println(FeeReportGenerator.generateConsolidatedReport(exchanges));
        
        // Print individual exchange reports
        for (ExchangeService exchange : exchanges) {
            System.out.println("\n===== " + exchange.getExchangeName().toUpperCase() + " FEE SUMMARY =====");
            System.out.println(exchange.getFeeTracker().generateFeeSummaryReport());
        }
    }
    
    /**
     * Runs a direct comparison between exchanges for all common symbols.
     * This provides a more detailed view of the price differences.
     * Note: This is the original method which doesn't use symbol normalization.
     */
    private static void runDirectArbitrageComparison(List<ExchangeService> exchanges, Set<String> commonSymbols) {
        if (commonSymbols.isEmpty()) {
            System.out.println("No common symbols to compare between exchanges.");
            return;
        }
        
        // Clear the console for better readability
        System.out.print("\033[H\033[2J");
        System.out.flush();
        
        System.out.println("=== Real-time Arbitrage Opportunities ===\n");
        
        for (int i = 0; i < exchanges.size(); i++) {
            for (int j = i + 1; j < exchanges.size(); j++) {
                ExchangeService exA = exchanges.get(i);
                ExchangeService exB = exchanges.get(j);
                
                for (String symbol : commonSymbols) {
                    try {
                        TradingPair pair = new TradingPair(symbol);
                        ExchangeToExchangeArbitrage arbitrageCalc = new ExchangeToExchangeArbitrage(exA, exB);
                        ArbitrageOpportunity opportunity = arbitrageCalc.calculateArbitrage(pair);

                        if (opportunity != null && opportunity.getProfitPercent() > MIN_PROFIT_PERCENT) {
                            // Get fee information
                            String buyExchange = opportunity.getExchangeBuy();
                            String sellExchange = opportunity.getExchangeSell();
                            double buyPrice = opportunity.getBuyPrice();
                            double sellPrice = opportunity.getSellPrice();
                            
                            // Determine which exchange service is the buy/sell exchange
                            ExchangeService buyExchangeService = null;
                            ExchangeService sellExchangeService = null;
                            
                            if (buyExchange.equals(exA.getExchangeName())) {
                                buyExchangeService = exA;
                                sellExchangeService = exB;
                            } else {
                                buyExchangeService = exB;
                                sellExchangeService = exA;
                            }
                            
                            // Get fee objects
                            Fee buyFee = buyExchangeService.getTakerFee();
                            Fee sellFee = sellExchangeService.getTakerFee();
                            
                            // Calculate quantity based on price
                            double quantity;
                            if (buyPrice < 0.001) {
                                quantity = 1000000; // 1 million units for micro-priced tokens like SHIB
                            } else if (buyPrice < 1.0) {
                                quantity = 1000; // 1,000 units for tokens under $1
                            } else if (buyPrice < 100.0) {
                                quantity = 10; // 10 units for tokens under $100
                            } else {
                                quantity = 0.01; // 0.01 units for expensive tokens like BTC
                            }
                            
                            // Calculate fee percentages
                            double buyFeePercent = buyFee.calculateFee(buyPrice * quantity) / (buyPrice * quantity) * 100;
                            double sellFeePercent = sellFee.calculateFee(sellPrice * quantity) / (sellPrice * quantity) * 100;
                            double totalFeePercent = buyFeePercent + sellFeePercent;
                            
                            // Calculate raw price difference percentage
                            double priceDiffPercent = ((sellPrice / buyPrice) - 1) * 100;
                            
                            // Format the opportunity display
                            StringBuilder sb = new StringBuilder();
                            sb.append(String.format(">>> %s: Buy on %s at %s, Sell on %s at %s\n", 
                                opportunity.getNormalizedSymbol(),
                                opportunity.getExchangeBuy(),
                                formatPrice(opportunity.getBuyPrice()),
                                opportunity.getExchangeSell(),
                                formatPrice(opportunity.getSellPrice())));
                            
                            sb.append(String.format("    Profit: %.4f%% | Success Rate: %.2f%%\n", 
                                opportunity.getProfitPercent(),
                                opportunity.getSuccessfulArbitragePercent()));
                            
                            // Add fee information
                            sb.append(String.format("    Fees: Buy: %.4f%% (%s) | Sell: %.4f%% (%s) | Total: %.4f%%\n",
                                buyFeePercent, 
                                buyFee.getDescription(),
                                sellFeePercent,
                                sellFee.getDescription(),
                                totalFeePercent));
                                
                            sb.append(String.format("    Price Diff: %.4f%% | Net After Fees: %.4f%%\n",
                                priceDiffPercent,
                                priceDiffPercent - totalFeePercent));
                            
                            // Add risk assessment if available
                            RiskAssessment risk = opportunity.getRiskAssessment();
                            if (risk != null) {
                                sb.append(String.format("    Risk Score: %.2f | Liquidity: %.2f | Volatility: %.2f\n",
                                    risk.getOverallRiskScore(),
                                    risk.getLiquidityScore(),
                                    risk.getVolatilityScore()));
                            }
                            
                            System.out.println(sb.toString());
                        }
                    } catch (Exception e) {
                        System.err.println("Error calculating arbitrage for " + symbol + ": " + e.getMessage());
                    }
                }
            }
        }
        
        System.out.println("\nLast updated: " + new Date());
        System.out.println("=====================================");
    }
    
    /**
     * Formats price values with appropriate scientific notation for small values
     */
    private static String formatPrice(double price) {
        if (price < 0.001) {
            return String.format("%.4E", price);
        } else {
            return String.format("%.8f", price);
        }
    }

    /**
     * Normalizes a symbol to a standard format.
     */
    private static String normalizeSymbol(String symbol) {
        // Remove any separators like '-' or '/'
        String normalized = symbol.replace("-", "").replace("/", "").toUpperCase();
        
        // Handle special cases like XBT (Kraken's name for BTC)
        if (normalized.startsWith("XBT")) {
            normalized = "BTC" + normalized.substring(3);
        }
        
        // Strip exchange-specific suffixes if needed
        // Some exchanges add specific identifiers we don't need for comparison
        if (normalized.endsWith(".P") || normalized.endsWith(".T")) {
            normalized = normalized.substring(0, normalized.length() - 2);
        }
        
        return normalized;
    }

    /**
     * Find common trading symbols across all exchanges using normalization.
     */
    private static List<String> findCommonSymbols(List<ExchangeService> exchangeServices) {
        if (exchangeServices.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Create maps to store normalized symbol -> original symbol for each exchange
        Map<ExchangeService, Map<String, String>> exchangeSymbolMaps = new HashMap<>();
        
        // Populate maps with normalized symbols for each exchange
        for (ExchangeService exchange : exchangeServices) {
            Map<String, String> normalizedMap = new HashMap<>();
            List<TradingPair> pairs = exchange.getTradingPairs();
            
            // Skip exchanges with no trading pairs
            if (pairs == null || pairs.isEmpty()) {
                System.out.println("Warning: No trading pairs available for " + exchange.getExchangeName());
                continue;
            }
            
            for (TradingPair pair : pairs) {
                String originalSymbol = pair.getSymbol();
                String normalizedSymbol = normalizeSymbol(originalSymbol);
                normalizedMap.put(normalizedSymbol, originalSymbol);
            }
            
            exchangeSymbolMaps.put(exchange, normalizedMap);
            System.out.println("Found " + normalizedMap.size() + " trading pairs for " + exchange.getExchangeName());
        }
        
        // Find common normalized symbols across all exchanges
        Set<String> commonNormalizedSymbols = null;
        
        for (Map<String, String> symbolMap : exchangeSymbolMaps.values()) {
            if (commonNormalizedSymbols == null) {
                commonNormalizedSymbols = new HashSet<>(symbolMap.keySet());
                    } else {
                commonNormalizedSymbols.retainAll(symbolMap.keySet());
            }
        }
        
        if (commonNormalizedSymbols == null || commonNormalizedSymbols.isEmpty()) {
            System.out.println("No common symbols found across exchanges after normalization");
            return new ArrayList<>();
        }
        
        // Create mapping of normalized symbols to original symbols for each exchange
        // This will help us later when we need to query data for specific exchange formats
        exchangeSymbolMap.clear(); // Clear any previous mappings
        
        for (String normalizedSymbol : commonNormalizedSymbols) {
            StringBuilder sb = new StringBuilder("Found common symbol: " + normalizedSymbol + " (");
            boolean first = true;
            
            for (ExchangeService exchange : exchangeSymbolMaps.keySet()) {
                Map<String, String> symbolMap = exchangeSymbolMaps.get(exchange);
                String originalSymbol = symbolMap.get(normalizedSymbol);
                
                if (originalSymbol != null) {
                    if (!first) {
                        sb.append(", ");
                    }
                    sb.append(exchange.getExchangeName()).append(": ").append(originalSymbol);
                    first = false;
                    
                    // Store the mapping for later use
                    if (!exchangeSymbolMap.containsKey(exchange)) {
                        exchangeSymbolMap.put(exchange, new HashMap<>());
                    }
                    exchangeSymbolMap.get(exchange).put(normalizedSymbol, originalSymbol);
                }
            }
            sb.append(")");
            System.out.println(sb.toString());
        }
        
        return new ArrayList<>(commonNormalizedSymbols);
    }

    /**
     * Schedules periodic arbitrage scans.
     */
    private static void schedulePeriodicScans(List<ExchangeService> exchanges, Set<String> commonSymbols) {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        
        Runnable task = () -> {
            try {
                System.out.println("\n[" + new Date() + "] Scanning for arbitrage opportunities...");
                runDirectArbitrageComparison(exchanges, commonSymbols);
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        
        // Schedule the task to run every 30 seconds
        executor.scheduleAtFixedRate(task, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Runs a comparison using static data for testing.
     */
    private static void runDirectComparisonStatic(List<ExchangeService> exchanges, List<String> commonSymbols) {
        System.out.println("\n=== Running Direct Exchange-to-Exchange Comparison ===");
        
        if (commonSymbols == null || commonSymbols.isEmpty()) {
            System.out.println("No common symbols to compare between exchanges.");
            return;
        }
        
        List<ArbitrageOpportunity> opportunities = new ArrayList<>();
        
        for (String normalizedSymbol : commonSymbols) {
            for (int i = 0; i < exchanges.size(); i++) {
                for (int j = i + 1; j < exchanges.size(); j++) {
                    ExchangeService exchangeA = exchanges.get(i);
                    ExchangeService exchangeB = exchanges.get(j);
                    
                    // Get the exchange-specific symbols
                    if (!exchangeSymbolMap.containsKey(exchangeA) || !exchangeSymbolMap.containsKey(exchangeB)) {
                        continue;
                    }
                    
                    String symbolA = exchangeSymbolMap.get(exchangeA).get(normalizedSymbol);
                    String symbolB = exchangeSymbolMap.get(exchangeB).get(normalizedSymbol);
                    
                    if (symbolA == null || symbolB == null) {
                        continue;
                    }
                    
                    try {
                        // Get ticker data for both exchanges
                        Ticker tickerA = exchangeA.getTickerData(symbolA);
                        Ticker tickerB = exchangeB.getTickerData(symbolB);
                        
                        if (tickerA == null || tickerB == null) {
                            continue;
                        }
                        
                        // Check for arbitrage opportunity A -> B (buy on A, sell on B)
                        // We'll use the taker fee for both since market orders are typical for arbitrage execution
                        double buyPrice = tickerA.getAskPrice();
                        double sellPrice = tickerB.getBidPrice();
                        
                        // Dynamically adjust quantity based on token price
                        double quantity;
                        if (buyPrice < 0.001) {
                            quantity = 1000000; // 1 million units for micro-priced tokens like SHIB
                        } else if (buyPrice < 1.0) {
                            quantity = 1000; // 1,000 units for tokens under $1
                        } else if (buyPrice < 100.0) {
                            quantity = 10; // 10 units for tokens under $100
                        } else {
                            quantity = 0.01; // 0.01 units for expensive tokens like BTC
                        }
                        
                        // Use the new fee system to calculate fees for both exchanges
                        Fee buyFee = exchangeA.getTakerFee();
                        Fee sellFee = exchangeB.getTakerFee();
                        
                        // Calculate profit using the FeeCalculator
                        double profit = FeeCalculator.calculateArbitrageProfit(buyPrice, sellPrice, quantity, buyFee, sellFee);
                        double profitPercent = FeeCalculator.calculateArbitrageProfitPercentage(buyPrice, sellPrice, quantity, buyFee, sellFee);
                        
                        if (profitPercent > MIN_PROFIT_PERCENT) {
                            // Create risk calculator for this opportunity
                            RiskCalculator riskCalc = new RiskCalculator(MIN_PROFIT_PERCENT / 100);
                            
                            // Calculate risk assessment
                            // We'll pass the fee objects instead of just the fee percentages
                            RiskAssessment riskAssessment = riskCalc.calculateRisk(tickerA, tickerB, 
                                    buyFee.calculateFee(buyPrice * quantity) / (buyPrice * quantity), 
                                    sellFee.calculateFee(sellPrice * quantity) / (sellPrice * quantity));
                            
                            ArbitrageOpportunity opportunity = new ArbitrageOpportunity(
                                    normalizedSymbol,
                                    symbolA,
                                    symbolB,
                                    exchangeA.getExchangeName(),
                                    exchangeB.getExchangeName(),
                                    buyPrice,
                                    sellPrice,
                                    profitPercent
                            );
                            
                            // Set the risk assessment on the opportunity
                            opportunity.setRiskAssessment(riskAssessment);
                            
                            opportunities.add(opportunity);
                            
                            // Log detailed information about the opportunity
                            StringBuilder sb = new StringBuilder();
                            sb.append(String.format("Found opportunity: %s\n", normalizedSymbol));
                            sb.append(String.format("  Buy on %s at %s, Sell on %s at %s\n", 
                                    exchangeA.getExchangeName(), formatPrice(buyPrice), 
                                    exchangeB.getExchangeName(), formatPrice(sellPrice)));
                            
                            // Calculate fee percentages for better visibility
                            double buyFeePercent = buyFee.calculateFee(buyPrice * quantity) / (buyPrice * quantity) * 100;
                            double sellFeePercent = sellFee.calculateFee(sellPrice * quantity) / (sellPrice * quantity) * 100;
                            double totalFeePercent = buyFeePercent + sellFeePercent;
                            
                            // Calculate raw price difference percentage
                            double priceDiffPercent = ((sellPrice / buyPrice) - 1) * 100;
                            
                            // Verify that the profit calculation is consistent
                            double verifiedNetProfit = priceDiffPercent - totalFeePercent;
                            
                            sb.append(String.format("  Price difference: %.4f%%\n", priceDiffPercent));
                            sb.append(String.format("  Fee impact: Buy fee %.4f%%, Sell fee %.4f%% (total: %.4f%%)\n",
                                    buyFeePercent, sellFeePercent, totalFeePercent));
                            sb.append(String.format("  Net profit after fees: %.4f%%\n", verifiedNetProfit));
                            sb.append(String.format("  Success probability: %.2f%%\n", opportunity.getSuccessfulArbitragePercent()));
                            System.out.println(sb.toString());
                            
                            // Track the fees for this potential trade
                            exchangeA.calculateAndTrackFee(symbolA, buyPrice * quantity, false);
                            exchangeB.calculateAndTrackFee(symbolB, sellPrice * quantity, false);
                        }
                        
                        // Check for arbitrage opportunity B -> A (buy on B, sell on A)
                        buyPrice = tickerB.getAskPrice();
                        sellPrice = tickerA.getBidPrice();
                        
                        // Swap the fees for the reverse direction
                        buyFee = exchangeB.getTakerFee();
                        sellFee = exchangeA.getTakerFee();
                        
                        // Calculate profit using the FeeCalculator
                        profit = FeeCalculator.calculateArbitrageProfit(buyPrice, sellPrice, quantity, buyFee, sellFee);
                        profitPercent = FeeCalculator.calculateArbitrageProfitPercentage(buyPrice, sellPrice, quantity, buyFee, sellFee);
                        
                        if (profitPercent > MIN_PROFIT_PERCENT) {
                            // Create risk calculator for this opportunity
                            RiskCalculator riskCalc = new RiskCalculator(MIN_PROFIT_PERCENT / 100);
                            
                            // Calculate risk assessment with fee objects
                            RiskAssessment riskAssessment = riskCalc.calculateRisk(tickerB, tickerA, 
                                    buyFee.calculateFee(buyPrice * quantity) / (buyPrice * quantity), 
                                    sellFee.calculateFee(sellPrice * quantity) / (sellPrice * quantity));
                            
                            ArbitrageOpportunity opportunity = new ArbitrageOpportunity(
                                    normalizedSymbol,
                                    symbolB,
                                    symbolA,
                                    exchangeB.getExchangeName(),
                                    exchangeA.getExchangeName(),
                                    buyPrice,
                                    sellPrice,
                                    profitPercent
                            );
                            
                            // Set the risk assessment on the opportunity
                            opportunity.setRiskAssessment(riskAssessment);
                            
                            opportunities.add(opportunity);
                            
                            // Log detailed information about the opportunity
                            StringBuilder sb = new StringBuilder();
                            sb.append(String.format("Found opportunity: %s\n", normalizedSymbol));
                            sb.append(String.format("  Buy on %s at %s, Sell on %s at %s\n", 
                                    exchangeB.getExchangeName(), formatPrice(buyPrice), 
                                    exchangeA.getExchangeName(), formatPrice(sellPrice)));
                            
                            // Calculate fee percentages for better visibility
                            double buyFeePercent = buyFee.calculateFee(buyPrice * quantity) / (buyPrice * quantity) * 100;
                            double sellFeePercent = sellFee.calculateFee(sellPrice * quantity) / (sellPrice * quantity) * 100;
                            double totalFeePercent = buyFeePercent + sellFeePercent;
                            
                            // Calculate raw price difference percentage
                            double priceDiffPercent = ((sellPrice / buyPrice) - 1) * 100;
                            
                            // Verify that the profit calculation is consistent
                            double verifiedNetProfit = priceDiffPercent - totalFeePercent;
                            
                            sb.append(String.format("  Price difference: %.4f%%\n", priceDiffPercent));
                            sb.append(String.format("  Fee impact: Buy fee %.4f%%, Sell fee %.4f%% (total: %.4f%%)\n",
                                    buyFeePercent, sellFeePercent, totalFeePercent));
                            sb.append(String.format("  Net profit after fees: %.4f%%\n", verifiedNetProfit));
                            sb.append(String.format("  Success probability: %.2f%%\n", opportunity.getSuccessfulArbitragePercent()));
                            System.out.println(sb.toString());
                            
                            // Track the fees for this potential trade
                            exchangeB.calculateAndTrackFee(symbolB, buyPrice * quantity, false);
                            exchangeA.calculateAndTrackFee(symbolA, sellPrice * quantity, false);
                        }
                    } catch (Exception e) {
                        System.err.println("Error comparing " + symbolA + " on " + exchangeA.getExchangeName() + 
                                " with " + symbolB + " on " + exchangeB.getExchangeName() + ": " + e.getMessage());
                    }
                }
            }
        }
        
        if (opportunities.isEmpty()) {
            System.out.println("No arbitrage opportunities found above " + MIN_PROFIT_PERCENT + "% profit threshold.");
        } else {
            System.out.println("Found " + opportunities.size() + " arbitrage opportunities!");
            
            // Sort opportunities by profit percentage (descending)
            opportunities.sort((o1, o2) -> Double.compare(o2.getProfitPercent(), o1.getProfitPercent()));
            
            // Display the top opportunities with risk assessment
            int displayCount = Math.min(5, opportunities.size());
            System.out.println("\nTop " + displayCount + " opportunities with risk assessment:");
            for (int i = 0; i < displayCount; i++) {
                ArbitrageOpportunity opportunity = opportunities.get(i);
                RiskAssessment risk = opportunity.getRiskAssessment();
                
                System.out.println((i+1) + ". " + opportunity);
                System.out.println("   Risk Assessment Summary:");
                System.out.println("     Liquidity: " + String.format("%.2f", risk.getLiquidityScore()) + 
                                   ", Volatility: " + String.format("%.2f", risk.getVolatilityScore()) + 
                                   ", Fee Impact: " + String.format("%.2f", risk.getFeeImpact()));
                System.out.println("     Market Depth: " + String.format("%.2f", risk.getMarketDepthScore()) + 
                                   ", Execution Speed: " + String.format("%.2f", risk.getExecutionSpeedRisk()) + 
                                   ", Overall Risk: " + String.format("%.2f", risk.getOverallRiskScore()));
                
                // Print detailed fee information
                String buyExchange = opportunity.getExchangeBuy();
                String sellExchange = opportunity.getExchangeSell();
                ExchangeService buyExchangeService = exchanges.stream()
                    .filter(e -> e.getExchangeName().equals(buyExchange))
                    .findFirst().orElse(null);
                ExchangeService sellExchangeService = exchanges.stream()
                    .filter(e -> e.getExchangeName().equals(sellExchange))
                    .findFirst().orElse(null);
                
                if (buyExchangeService != null && sellExchangeService != null) {
                    System.out.println("   Fee Information:");
                    System.out.println("     Buy Exchange: " + buyExchange + " - Taker Fee: " + 
                                      buyExchangeService.getTakerFee().getDescription());
                    System.out.println("     Sell Exchange: " + sellExchange + " - Taker Fee: " + 
                                      sellExchangeService.getTakerFee().getDescription());
                }
            }
            
            // If fee reporting is enabled, show a fee summary for the discovered opportunities
            if (ENABLE_FEE_REPORTS) {
                System.out.println("\n=== Fee Impact on Arbitrage Opportunities ===");
                System.out.println("Fees can significantly impact arbitrage profitability. Current fee structures:");
                for (ExchangeService exchange : exchanges) {
                    System.out.println(exchange.getExchangeName() + ": Maker " + 
                                      exchange.getMakerFee().getDescription() + ", Taker " + 
                                      exchange.getTakerFee().getDescription());
                }
            }
        }
    }
}
