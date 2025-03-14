package org.example.data.model.fee;

/**
 * Interface representing a fee structure in the trading system.
 * This can be implemented by different fee types such as fixed fees,
 * percentage fees, or tiered fee structures.
 */
public interface Fee {
    
    /**
     * Calculate the fee amount for a given transaction.
     *
     * @param amount The transaction amount
     * @return The calculated fee amount
     */
    double calculateFee(double amount);
    
    /**
     * Gets a description of the fee structure.
     *
     * @return A string describing the fee structure
     */
    String getDescription();
    
    /**
     * Gets the type of fee.
     *
     * @return The fee type
     */
    FeeType getType();
    
    /**
     * Enumeration of fee types supported by the system.
     */
    enum FeeType {
        FIXED,      // Fixed amount fee regardless of transaction size
        PERCENTAGE, // Percentage-based fee proportional to transaction size
        TIERED      // Fee rate varies based on volume/tier thresholds
    }
} 