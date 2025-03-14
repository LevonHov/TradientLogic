package org.example.data.model.fee;

import java.util.Date;

/**
 * Represents a fee applied to a specific transaction.
 * This class stores details about a fee that was applied to a transaction,
 * including the fee amount, type, and related transaction information.
 */
public class TransactionFee {
    
    private String transactionId;
    private String exchangeName;
    private String tradingPair;
    private double amount;
    private Fee.FeeType feeType;
    private Date timestamp;
    private String description;
    private double originalFeeRate;
    private double appliedDiscount;
    private boolean isMakerFee;
    
    /**
     * Constructs a TransactionFee with the specified parameters.
     *
     * @param transactionId Unique identifier for the transaction
     * @param exchangeName The name of the exchange
     * @param tradingPair The trading pair involved
     * @param amount The fee amount
     * @param feeType The type of fee (FIXED, PERCENTAGE, TIERED)
     * @param timestamp When the fee was applied
     * @param description Additional details about the fee
     * @param originalFeeRate The original fee rate before discounts
     * @param appliedDiscount The discount applied (as a decimal)
     * @param isMakerFee Whether this was a maker fee (true) or taker fee (false)
     */
    public TransactionFee(String transactionId, String exchangeName, String tradingPair,
                        double amount, Fee.FeeType feeType, Date timestamp, String description,
                        double originalFeeRate, double appliedDiscount, boolean isMakerFee) {
        this.transactionId = transactionId;
        this.exchangeName = exchangeName;
        this.tradingPair = tradingPair;
        this.amount = amount;
        this.feeType = feeType;
        this.timestamp = timestamp;
        this.description = description;
        this.originalFeeRate = originalFeeRate;
        this.appliedDiscount = appliedDiscount;
        this.isMakerFee = isMakerFee;
    }
    
    /**
     * Constructs a TransactionFee with defaults for some values.
     *
     * @param transactionId Unique identifier for the transaction
     * @param exchangeName The name of the exchange
     * @param tradingPair The trading pair involved
     * @param amount The fee amount
     * @param feeType The type of fee
     */
    public TransactionFee(String transactionId, String exchangeName, String tradingPair,
                        double amount, Fee.FeeType feeType) {
        this(transactionId, exchangeName, tradingPair, amount, feeType, 
             new Date(), "Standard transaction fee", 0.0, 0.0, false);
    }
    
    // Getters
    
    public String getTransactionId() {
        return transactionId;
    }
    
    public String getExchangeName() {
        return exchangeName;
    }
    
    public String getTradingPair() {
        return tradingPair;
    }
    
    public double getAmount() {
        return amount;
    }
    
    public Fee.FeeType getFeeType() {
        return feeType;
    }
    
    public Date getTimestamp() {
        return timestamp;
    }
    
    public String getDescription() {
        return description;
    }
    
    public double getOriginalFeeRate() {
        return originalFeeRate;
    }
    
    public double getAppliedDiscount() {
        return appliedDiscount;
    }
    
    public boolean isMakerFee() {
        return isMakerFee;
    }
    
    /**
     * Calculate the fee savings from discounts.
     *
     * @return The amount saved due to applied discounts
     */
    public double getDiscountSavings() {
        if (appliedDiscount > 0 && feeType == Fee.FeeType.PERCENTAGE) {
            double effectiveRate = originalFeeRate * (1 - appliedDiscount);
            double undiscountedAmount = amount / (1 - appliedDiscount);
            return undiscountedAmount - amount;
        }
        return 0.0;
    }
    
    /**
     * Provides a human-readable format of the transaction fee.
     *
     * @return A string representation of the transaction fee
     */
    @Override
    public String toString() {
        return String.format("Fee: %.6f on %s for %s (%s) - %s", 
                            amount, exchangeName, tradingPair, 
                            isMakerFee ? "Maker" : "Taker", description);
    }
} 