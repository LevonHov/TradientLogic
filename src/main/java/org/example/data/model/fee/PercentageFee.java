package org.example.data.model.fee;

/**
 * Represents a percentage-based fee structure where the fee is calculated
 * as a percentage of the transaction amount.
 */
public class PercentageFee implements Fee {
    
    private final double percentage;
    private final String description;
    private final boolean isMakerFee;
    
    /**
     * Constructs a percentage fee with the specified rate.
     *
     * @param percentage The fee percentage as a decimal (e.g., 0.001 for 0.1%)
     * @param isMakerFee Whether this is a maker fee (true) or taker fee (false)
     * @param description Optional description of the fee
     */
    public PercentageFee(double percentage, boolean isMakerFee, String description) {
        this.percentage = percentage;
        this.isMakerFee = isMakerFee;
        this.description = description != null ? description : 
                String.format("%s fee of %.4f%%", 
                        (isMakerFee ? "Maker" : "Taker"), 
                        percentage * 100);
    }
    
    /**
     * Constructs a percentage fee with the specified rate.
     *
     * @param percentage The fee percentage as a decimal (e.g., 0.001 for 0.1%)
     * @param isMakerFee Whether this is a maker fee (true) or taker fee (false)
     */
    public PercentageFee(double percentage, boolean isMakerFee) {
        this(percentage, isMakerFee, null);
    }
    
    @Override
    public double calculateFee(double amount) {
        return amount * percentage;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    @Override
    public FeeType getType() {
        return FeeType.PERCENTAGE;
    }
    
    /**
     * Gets the fee percentage.
     *
     * @return The fee percentage as a decimal
     */
    public double getPercentage() {
        return percentage;
    }
    
    /**
     * Determines if this is a maker fee.
     *
     * @return true if this is a maker fee, false if it's a taker fee
     */
    public boolean isMakerFee() {
        return isMakerFee;
    }
} 