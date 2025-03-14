package org.example.data.model.fee;

/**
 * Represents a fixed fee structure where the fee is a constant amount
 * regardless of the transaction size.
 */
public class FixedFee implements Fee {
    
    private final double feeAmount;
    private final String description;
    
    /**
     * Constructs a fixed fee with the specified amount.
     *
     * @param feeAmount The fixed fee amount
     * @param description Optional description of the fee
     */
    public FixedFee(double feeAmount, String description) {
        this.feeAmount = feeAmount;
        this.description = description != null ? description : "Fixed fee of " + feeAmount;
    }
    
    /**
     * Constructs a fixed fee with the specified amount.
     *
     * @param feeAmount The fixed fee amount
     */
    public FixedFee(double feeAmount) {
        this(feeAmount, null);
    }
    
    @Override
    public double calculateFee(double amount) {
        return feeAmount;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    @Override
    public FeeType getType() {
        return FeeType.FIXED;
    }
    
    /**
     * Gets the fixed fee amount.
     *
     * @return The fixed fee amount
     */
    public double getFeeAmount() {
        return feeAmount;
    }
} 