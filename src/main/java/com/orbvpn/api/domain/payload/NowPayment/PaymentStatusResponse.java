package com.orbvpn.api.domain.payload.NowPayment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

/**
 * Response from NOWPayments API when checking payment status.
 * GET https://api.nowpayments.io/v1/payment/{payment_id}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentStatusResponse {

    @JsonProperty("payment_id")
    private String paymentId;

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
}
