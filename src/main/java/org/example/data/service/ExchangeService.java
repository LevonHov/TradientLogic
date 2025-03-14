package org.example.data.service;

import org.example.data.model.OrderBook;
import org.example.data.model.Ticker;
import org.example.data.model.TradingPair;
import org.example.data.model.fee.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ExchangeService {

    // Unique exchange identifier
    private String exchangeName;

    //------------------->Dont forget add here logo property <---------------------

    // Property to cache trading pairs
    private List<TradingPair> tradingPairs;

    // Cache for real-time ticker data
    protected ConcurrentHashMap<String, Ticker> tickerCache;

    // Cache for real-time order book data
    protected ConcurrentHashMap<String, OrderBook> orderBookCache;

    // Flag to track if WebSocket connection is active
    protected volatile boolean websocketConnected = false;

    // Fee structures
    private Fee makerFee;
    private Fee takerFee;
    
    // Fee tracker for this exchange
    private FeeTracker feeTracker;
    
    // The 30-day trading volume used for fee tier calculations
    private double thirtyDayTradingVolume;
    
    // Whether this exchange has BNB discount (Binance only)
    private boolean hasBnbDiscount;

    // Constructor to initialize the exchange name and the trading pairs cache
    public ExchangeService(String exchangeName, double fees) {
        this.exchangeName = exchangeName;
        this.tradingPairs = new ArrayList<>();
        this.tickerCache = new ConcurrentHashMap<>();
        this.orderBookCache = new ConcurrentHashMap<>();
        this.feeTracker = new FeeTracker();
        this.thirtyDayTradingVolume = 0.0;
        this.hasBnbDiscount = false;
        
        // Initialize with simple percentage fees to maintain backward compatibility
        this.makerFee = new PercentageFee(fees, true);
        this.takerFee = new PercentageFee(fees, false);
    }
    
    /**
     * Constructor with expanded fee options.
     *
     * @param exchangeName The exchange name
     * @param makerFee The maker fee
     * @param takerFee The taker fee
     */
    public ExchangeService(String exchangeName, Fee makerFee, Fee takerFee) {
        this.exchangeName = exchangeName;
        this.tradingPairs = new ArrayList<>();
        this.tickerCache = new ConcurrentHashMap<>();
        this.orderBookCache = new ConcurrentHashMap<>();
        this.feeTracker = new FeeTracker();
        this.thirtyDayTradingVolume = 0.0;
        this.hasBnbDiscount = false;
        this.makerFee = makerFee;
        this.takerFee = takerFee;
    }

    /**
     * Returns the cached list of trading pairs.
     */
    public List<TradingPair> getTradingPairs() {
        return tradingPairs;
    }

    /**
     * Updates the trading pairs cache with a new list.
     */
    protected void setTradingPairs(List<TradingPair> pairs) {
        this.tradingPairs = pairs;
    }

    /**
     * Fetches the list of trading pairs from the exchange API,
     * updates the internal cache, and returns the updated list.
     */
    public abstract List<TradingPair> fetchTradingPairs();

    /**
     * Retrieves the latest ticker data for the specified symbol.
     * Will try to use cached WebSocket data if available, otherwise falls back to REST.
     */
    public Ticker getTickerData(String symbol) {
        // Try to get from WebSocket cache first
        Ticker cachedTicker = tickerCache.get(symbol);
        if (cachedTicker != null) {
            return cachedTicker;
        }

        // Fall back to REST API if WebSocket data is not available
        return fetchTickerDataREST(symbol);
    }

    /**
     * Retrieves the latest ticker data using REST API (fallback method)
     */
    protected abstract Ticker fetchTickerDataREST(String symbol);

    /**
     * Retrieves the current order book for the specified trading pair.
     * Will try to use cached WebSocket data if available, otherwise falls back to REST.
     */
    public OrderBook getOrderBook(String symbol) {
        // Try to get from WebSocket cache first
        OrderBook cachedOrderBook = orderBookCache.get(symbol);
        if (cachedOrderBook != null) {
            return cachedOrderBook;
        }

        // Fall back to REST API if WebSocket data is not available
        return fetchOrderBookREST(symbol);
    }

    /**
     * Retrieves the current order book using REST API (fallback method)
     */
    protected abstract OrderBook fetchOrderBookREST(String symbol);

    /**
     * Initializes WebSocket connections for market data streaming
     *
     * @param symbols List of symbols to subscribe to
     * @return true if successfully connected, false otherwise
     */
    public abstract boolean initializeWebSocket(List<String> symbols);

    /**
     * Closes the WebSocket connections
     */
    public abstract void closeWebSocket();

    /**
     * Returns the exchange's unique name.
     */
    public String getExchangeName() {
        return exchangeName;
    }

    /**
     * Get the maker fee for this exchange.
     * 
     * @return The maker fee structure
     */
    public Fee getMakerFee() {
        return makerFee;
    }
    
    /**
     * Get the taker fee for this exchange.
     * 
     * @return The taker fee structure
     */
    public Fee getTakerFee() {
        return takerFee;
    }
    
    /**
     * Update the fee structures based on trading volume.
     * 
     * @param thirtyDayVolume The 30-day trading volume in USD
     */
    public void updateFeesTiers(double thirtyDayVolume) {
        this.thirtyDayTradingVolume = thirtyDayVolume;
        
        // Use the factory to create appropriate fee structures
        ExchangeFeeFactory feeFactory = ExchangeFeeFactory.getInstance();
        this.makerFee = feeFactory.createFee(exchangeName, thirtyDayVolume, true, hasBnbDiscount);
        this.takerFee = feeFactory.createFee(exchangeName, thirtyDayVolume, false, hasBnbDiscount);
    }
    
    /**
     * Set whether BNB discount is applied (for Binance only).
     * 
     * @param hasBnbDiscount true if BNB discount is applied
     */
    public void setBnbDiscount(boolean hasBnbDiscount) {
        if (this.hasBnbDiscount != hasBnbDiscount) {
            this.hasBnbDiscount = hasBnbDiscount;
            // Refresh fee structures
            updateFeesTiers(thirtyDayTradingVolume);
        }
    }
    
    /**
     * Get the fee tracker for this exchange.
     * 
     * @return The fee tracker
     */
    public FeeTracker getFeeTracker() {
        return feeTracker;
    }
    
    /**
     * Calculate and track the fee for a transaction.
     * 
     * @param tradingPair The trading pair symbol
     * @param amount The transaction amount
     * @param isMaker Whether this is a maker order (true) or taker order (false)
     * @return The calculated fee amount
     */
    public double calculateAndTrackFee(String tradingPair, double amount, boolean isMaker) {
        Fee fee = isMaker ? makerFee : takerFee;
        double feeAmount = fee.calculateFee(amount);
        
        // Generate a unique transaction ID
        String transactionId = UUID.randomUUID().toString();
        
        // Track the fee
        TransactionFee transactionFee = new TransactionFee(
                transactionId, 
                exchangeName, 
                tradingPair,
                feeAmount,
                fee.getType(),
                null, // use current time
                fee.getDescription(),
                isMaker ? 
                    (makerFee instanceof PercentageFee ? ((PercentageFee)makerFee).getPercentage() : 0.0) : 
                    (takerFee instanceof PercentageFee ? ((PercentageFee)takerFee).getPercentage() : 0.0),
                hasBnbDiscount ? 0.25 : 0.0, // discount rate
                isMaker
        );
        
        feeTracker.trackFee(transactionFee);
        
        return feeAmount;
    }
    
    /**
     * Calculate the buy fee for a transaction.
     * 
     * @param price The price
     * @param quantity The quantity
     * @return The fee amount
     */
    public double calculateBuyFee(double price, double quantity) {
        double amount = price * quantity;
        return takerFee.calculateFee(amount);
    }
    
    /**
     * Calculate the sell fee for a transaction.
     * 
     * @param price The price
     * @param quantity The quantity
     * @return The fee amount
     */
    public double calculateSellFee(double price, double quantity) {
        double amount = price * quantity;
        return takerFee.calculateFee(amount);
    }

    /**
     * Checks if the WebSocket connection is active
     */
    public boolean isWebSocketConnected() {
        return websocketConnected;
    }

    /**
     * Gets the trading fee for this exchange.
     *
     * @return The trading fee as a decimal (e.g., 0.001 for 0.1%)
     * @deprecated Use getMakerFee() or getTakerFee() instead
     */
    @Deprecated
    public double getTradingFees() {
        // For backward compatibility, return the taker fee
        if (takerFee instanceof PercentageFee) {
            return ((PercentageFee)takerFee).getPercentage();
        }
        return 0.001; // Default to 0.1% if not a percentage fee
    }
    
    /**
     * Gets the legacy fee value for backward compatibility.
     * 
     * @deprecated Use getMakerFee() or getTakerFee() instead
     */
    @Deprecated
    public double getFees() {
        return getTradingFees();
    }
}