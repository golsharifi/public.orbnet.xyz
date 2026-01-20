package com.orbvpn.api.domain.payload.NowPayment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

/**
 * IPN (Instant Payment Notification) callback payload from NOWPayments.
 * This is sent to your ipn_callback_url when payment status changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IpnCallbackPayload {

    @JsonProperty("payment_id")
    private Long paymentId;

    @JsonProperty("payment_status")
    private String paymentStatus;

    @JsonProperty("pay_address")
    private String payAddress;

    @JsonProperty("price_amount")
    private BigDecimal priceAmount;

    @JsonProperty("price_currency")
    private String priceCurrency;

    @JsonProperty("pay_amount")
    private BigDecimal payAmount;

    @JsonProperty("actually_paid")
    private BigDecimal actuallyPaid;

    @JsonProperty("pay_currency")
    private String payCurrency;

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("order_description")
    private String orderDescription;

    @JsonProperty("purchase_id")
    private String purchaseId;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("outcome_amount")
    private BigDecimal outcomeAmount;

    @JsonProperty("outcome_currency")
    private String outcomeCurrency;

    @JsonProperty("payin_extra_id")
    private String payinExtraId;

    /**
     * Check if payment is in a successful final state
     */
    public boolean isSuccessful() {
        return "finished".equalsIgnoreCase(paymentStatus) ||
               "confirmed".equalsIgnoreCase(paymentStatus);
    }

    /**
     * Check if payment is partially paid
     */
    public boolean isPartiallyPaid() {
        return "partially_paid".equalsIgnoreCase(paymentStatus);
    }

    /**
     * Check if payment has failed or expired
     */
    public boolean isFailed() {
        return "failed".equalsIgnoreCase(paymentStatus) ||
               "expired".equalsIgnoreCase(paymentStatus) ||
               "refunded".equalsIgnoreCase(paymentStatus);
    }
}
