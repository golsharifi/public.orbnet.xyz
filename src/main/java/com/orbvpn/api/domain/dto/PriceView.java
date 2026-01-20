package com.orbvpn.api.domain.dto;

import java.math.BigDecimal;

public class PriceView {
    private double discountedPrice;
    private BigDecimal savingRate;
    private BigDecimal discountRate;
    private int durationMonths; // Add this line

    // Constructor
    public PriceView(double discountedPrice, BigDecimal savingRate, BigDecimal discountRate, int durationMonths) {
        this.discountedPrice = discountedPrice;
        this.savingRate = savingRate;
        this.discountRate = discountRate;
        this.durationMonths = durationMonths; // Add this line
    }

    // Getters
    public double getDiscountedPrice() { return discountedPrice; }
    public BigDecimal getSavingRate() { return savingRate; }
    public BigDecimal getDiscountRate() { return discountRate; }
    public int getDurationMonths() { return durationMonths; } // Add this line
}
