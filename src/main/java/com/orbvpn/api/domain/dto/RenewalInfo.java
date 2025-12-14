package com.orbvpn.api.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RenewalInfo {

    @JsonProperty("originalTransactionId")
    private String originalTransactionId;

    @JsonProperty("productId")
    private String productId;

    @JsonProperty("autoRenewProductId")
    private String autoRenewProductId;

    @JsonProperty("autoRenewStatus")
    private int autoRenewStatus;

    @JsonProperty("isTrialPeriod")
    private Boolean isTrialPeriod;

    @JsonProperty("priceConsentStatus")
    private Integer priceConsentStatus;

    @JsonProperty("expirationIntent")
    private Integer expirationIntent;

    // Getters and setters

    public String getOriginalTransactionId() {
        return originalTransactionId;
    }

    public void setOriginalTransactionId(String originalTransactionId) {
        this.originalTransactionId = originalTransactionId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getAutoRenewProductId() {
        return autoRenewProductId;
    }

    public void setAutoRenewProductId(String autoRenewProductId) {
        this.autoRenewProductId = autoRenewProductId;
    }

    public int getAutoRenewStatus() {
        return autoRenewStatus;
    }

    public void setAutoRenewStatus(int autoRenewStatus) {
        this.autoRenewStatus = autoRenewStatus;
    }

    public Boolean getIsTrialPeriod() {
        return isTrialPeriod;
    }

    public void setIsTrialPeriod(Boolean isTrialPeriod) {
        this.isTrialPeriod = isTrialPeriod;
    }

    public Integer getPriceConsentStatus() {
        return priceConsentStatus;
    }

    public void setPriceConsentStatus(Integer priceConsentStatus) {
        this.priceConsentStatus = priceConsentStatus;
    }

    public Integer getExpirationIntent() {
        return expirationIntent;
    }

    public void setExpirationIntent(Integer expirationIntent) {
        this.expirationIntent = expirationIntent;
    }
}