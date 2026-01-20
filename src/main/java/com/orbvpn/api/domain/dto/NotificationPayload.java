package com.orbvpn.api.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationPayload {

    @JsonProperty("notificationType")
    private String notificationType;

    @JsonProperty("subtype")
    private String subtype;

    @JsonProperty("data")
    private TransactionData data;

    @JsonProperty("version")
    private String version;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionData {
        @JsonProperty("bundleId")
        private String bundleId;

        @JsonProperty("productId")
        private String productId;

        @JsonProperty("originalTransactionId")
        private String originalTransactionId;

        @JsonProperty("transactionId")
        private String transactionId;

        @JsonProperty("expiresDate")
        private Long expiresDateMs;

        @JsonProperty("isTrialPeriod")
        private Boolean isTrialPeriod;

        @JsonProperty("inAppOwnershipType")
        private String ownershipType;

        @JsonProperty("environment")
        private String environment;
    }
}