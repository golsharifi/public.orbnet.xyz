package com.orbvpn.api.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionInfo {

    @JsonProperty("transactionId")
    private String transactionId;

    @JsonProperty("originalTransactionId")
    private String originalTransactionId;

    @JsonProperty("productId")
    private String productId;

    @JsonProperty("purchaseDate")
    private Long purchaseDate; // Wrapper class allows null

    @JsonProperty("expiresDate")
    private Long expiresDate;

    // Add missing fields
    @JsonProperty("webOrderLineItemId")
    private String webOrderLineItemId;

    @JsonProperty("bundleId")
    private String bundleId;

    @JsonProperty("subscriptionGroupIdentifier")
    private String subscriptionGroupIdentifier;

    @JsonProperty("quantity")
    private int quantity;

    @JsonProperty("type")
    private String type;

    @JsonProperty("inAppOwnershipType")
    private String inAppOwnershipType;

    @JsonProperty("signedDate")
    private Long signedDate;

    @JsonProperty("offerType")
    private int offerType;

    @JsonProperty("environment")
    private String environment;

    @JsonProperty("transactionReason")
    private String transactionReason;

    @JsonProperty("storefront")
    private String storefront;

    @JsonProperty("storefrontId")
    private String storefrontId;

    @JsonProperty("isTrialPeriod")
    private boolean isTrialPeriod;

    @JsonProperty("price")
    private Long price;

    @JsonProperty("currency")
    private String currency;

    // Add getters and setters
    public Long getPrice() {
        return price;
    }

    public void setPrice(Long price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

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

    public Long getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(Long purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public Long getExpiresDate() {
        return expiresDate;
    }

    public void setExpiresDate(Long expiresDate) {
        this.expiresDate = expiresDate;
    }

    public String getWebOrderLineItemId() {
        return webOrderLineItemId;
    }

    public void setWebOrderLineItemId(String webOrderLineItemId) {
        this.webOrderLineItemId = webOrderLineItemId;
    }

    public String getBundleId() {
        return bundleId;
    }

    public void setBundleId(String bundleId) {
        this.bundleId = bundleId;
    }

    public String getSubscriptionGroupIdentifier() {
        return subscriptionGroupIdentifier;
    }

    public void setSubscriptionGroupIdentifier(String subscriptionGroupIdentifier) {
        this.subscriptionGroupIdentifier = subscriptionGroupIdentifier;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getInAppOwnershipType() {
        return inAppOwnershipType;
    }

    public void setInAppOwnershipType(String inAppOwnershipType) {
        this.inAppOwnershipType = inAppOwnershipType;
    }

    public Long getSignedDate() {
        return signedDate;
    }

    public void setSignedDate(Long signedDate) {
        this.signedDate = signedDate;
    }

    public int getOfferType() {
        return offerType;
    }

    public void setOfferType(int offerType) {
        this.offerType = offerType;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getTransactionReason() {
        return transactionReason;
    }

    public void setTransactionReason(String transactionReason) {
        this.transactionReason = transactionReason;
    }

    public String getStorefront() {
        return storefront;
    }

    public void setStorefront(String storefront) {
        this.storefront = storefront;
    }

    public String getStorefrontId() {
        return storefrontId;
    }

    public void setStorefrontId(String storefrontId) {
        this.storefrontId = storefrontId;
    }

    public boolean getIsTrialPeriod() {
        return isTrialPeriod;
    }

    public void setIsTrialPeriod(boolean isTrialPeriod) {
        this.isTrialPeriod = isTrialPeriod;
    }
}