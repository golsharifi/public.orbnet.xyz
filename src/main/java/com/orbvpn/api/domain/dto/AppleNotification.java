package com.orbvpn.api.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AppleNotification {

    @JsonProperty("notificationType")
    private String notificationType;

    @JsonProperty("subtype")
    private String subtype;

    @JsonProperty("notificationUUID")
    private String notificationUUID;

    @JsonProperty("data")
    private Data data;

    @JsonProperty("version")
    private String version;

    @JsonProperty("signedDate")
    private long signedDate;

    // Getters and setters for AppleNotification fields

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public String getSubtype() {
        return subtype;
    }

    public void setSubtype(String subtype) {
        this.subtype = subtype;
    }

    public String getNotificationUUID() {
        return notificationUUID;
    }

    public void setNotificationUUID(String notificationUUID) {
        this.notificationUUID = notificationUUID;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public long getSignedDate() {
        return signedDate;
    }

    public void setSignedDate(long signedDate) {
        this.signedDate = signedDate;
    }

    // Inner class for 'data' field
    public static class Data {

        @JsonProperty("appAppleId")
        private long appAppleId;

        @JsonProperty("bundleId")
        private String bundleId;

        @JsonProperty("bundleVersion")
        private String bundleVersion;

        @JsonProperty("environment")
        private String environment;

        @JsonProperty("signedTransactionInfo")
        private String signedTransactionInfo;

        @JsonProperty("signedRenewalInfo")
        private String signedRenewalInfo;

        @JsonProperty("status")
        private int status;

        @JsonProperty("consumptionRequestReason")
        private String consumptionRequestReason;

        // Getters and setters for Data fields

        public long getAppAppleId() {
            return appAppleId;
        }

        public void setAppAppleId(long appAppleId) {
            this.appAppleId = appAppleId;
        }

        public String getBundleId() {
            return bundleId;
        }

        public void setBundleId(String bundleId) {
            this.bundleId = bundleId;
        }

        public String getBundleVersion() {
            return bundleVersion;
        }

        public void setBundleVersion(String bundleVersion) {
            this.bundleVersion = bundleVersion;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public String getSignedTransactionInfo() {
            return signedTransactionInfo;
        }

        public void setSignedTransactionInfo(String signedTransactionInfo) {
            this.signedTransactionInfo = signedTransactionInfo;
        }

        public String getSignedRenewalInfo() {
            return signedRenewalInfo;
        }

        public void setSignedRenewalInfo(String signedRenewalInfo) {
            this.signedRenewalInfo = signedRenewalInfo;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public String getConsumptionRequestReason() {
            return consumptionRequestReason; // Getter for consumptionRequestReason
        }

        public void setConsumptionRequestReason(String consumptionRequestReason) {
            this.consumptionRequestReason = consumptionRequestReason; // Setter for consumptionRequestReason
        }
    }

    public String getBundleId() {
        return data != null ? data.getBundleId() : null;
    }

}