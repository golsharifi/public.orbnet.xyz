package com.orbvpn.api.domain.payload.YandexPay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

/**
 * Webhook callback payload from Yandex Pay.
 * Sent when payment status changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookPayload {

    @JsonProperty("event")
    private String event;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("merchantId")
    private String merchantId;

    @JsonProperty("operationId")
    private String operationId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("paymentMethod")
    private String paymentMethod;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("reasonCode")
    private String reasonCode;

    @JsonProperty("metadata")
    private String metadata;

    @JsonProperty("created")
    private String created;

    @JsonProperty("updated")
    private String updated;

    // Event types
    public static final String EVENT_ORDER_CREATED = "ORDER_CREATED";
    public static final String EVENT_ORDER_PAID = "ORDER_PAID";
    public static final String EVENT_ORDER_CAPTURED = "ORDER_CAPTURED";
    public static final String EVENT_ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String EVENT_ORDER_REFUNDED = "ORDER_REFUNDED";
    public static final String EVENT_ORDER_FAILED = "ORDER_FAILED";

    // Status values
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_AUTHORIZED = "AUTHORIZED";
    public static final String STATUS_CAPTURED = "CAPTURED";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_FAILED = "FAILED";

    /**
     * Check if this is a successful payment event
     */
    public boolean isSuccessfulPayment() {
        return EVENT_ORDER_PAID.equalsIgnoreCase(event) ||
               EVENT_ORDER_CAPTURED.equalsIgnoreCase(event) ||
               STATUS_CAPTURED.equalsIgnoreCase(status) ||
               STATUS_CONFIRMED.equalsIgnoreCase(status);
    }

    /**
     * Check if this is a failed payment event
     */
    public boolean isFailedPayment() {
        return EVENT_ORDER_FAILED.equalsIgnoreCase(event) ||
               EVENT_ORDER_CANCELLED.equalsIgnoreCase(event) ||
               STATUS_FAILED.equalsIgnoreCase(status) ||
               STATUS_CANCELLED.equalsIgnoreCase(status);
    }

    /**
     * Check if this is a refund event
     */
    public boolean isRefundEvent() {
        return EVENT_ORDER_REFUNDED.equalsIgnoreCase(event) ||
               STATUS_REFUNDED.equalsIgnoreCase(status);
    }
}
