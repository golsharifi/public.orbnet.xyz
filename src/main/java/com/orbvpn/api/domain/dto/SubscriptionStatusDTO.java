package com.orbvpn.api.domain.dto;

import java.time.LocalDateTime;

public class SubscriptionStatusDTO {

    private String productId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean isActive;

    public SubscriptionStatusDTO(String productId, LocalDateTime startDate, LocalDateTime endDate, boolean isActive) {
        this.productId = productId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.isActive = isActive;
    }

    // Getters and Setters
    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}