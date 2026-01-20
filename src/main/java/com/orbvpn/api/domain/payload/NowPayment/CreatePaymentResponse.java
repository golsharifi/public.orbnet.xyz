package com.orbvpn.api.domain.payload.NowPayment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

/**
 * Response from NOWPayments API when creating a payment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreatePaymentResponse {

    /**
     * NOWPayments unique payment ID
     */
    @JsonProperty("payment_id")
    private String paymentId;

    /**
     * Payment status: waiting, confirming, confirmed, sending, partially_paid, finished, failed, refunded, expired
     */
    @JsonProperty("payment_status")
    private String paymentStatus;

    /**
     * The cryptocurrency wallet address for payment
     */
    @JsonProperty("pay_address")
    private String payAddress;

    /**
     * Price amount in fiat
     */
    @JsonProperty("price_amount")
    private BigDecimal priceAmount;

    /**
     * Fiat currency
     */
    @JsonProperty("price_currency")
    private String priceCurrency;

    /**
     * Amount to pay in cryptocurrency
     */
    @JsonProperty("pay_amount")
    private BigDecimal payAmount;

    /**
     * Cryptocurrency to pay with
     */
    @JsonProperty("pay_currency")
    private String payCurrency;

    /**
     * Your internal order ID
     */
    @JsonProperty("order_id")
    private String orderId;

    /**
     * Order description
     */
    @JsonProperty("order_description")
    private String orderDescription;

    /**
     * IPN callback URL
     */
    @JsonProperty("ipn_callback_url")
    private String ipnCallbackUrl;

    /**
     * When the payment was created
     */
    @JsonProperty("created_at")
    private String createdAt;

    /**
     * When the payment was last updated
     */
    @JsonProperty("updated_at")
    private String updatedAt;

    /**
     * Purchase ID
     */
    @JsonProperty("purchase_id")
    private String purchaseId;

    /**
     * Amount actually paid
     */
    @JsonProperty("actually_paid")
    private BigDecimal actuallyPaid;

    /**
     * Outcome amount
     */
    @JsonProperty("outcome_amount")
    private BigDecimal outcomeAmount;

    /**
     * Outcome currency
     */
    @JsonProperty("outcome_currency")
    private String outcomeCurrency;

    /**
     * Smart contract address (for token payments)
     */
    @JsonProperty("smart_contract")
    private String smartContract;

    /**
     * Network for the payment
     */
    @JsonProperty("network")
    private String network;

    /**
     * Extra payment ID (for some currencies)
     */
    @JsonProperty("payin_extra_id")
    private String payinExtraId;

    /**
     * Burning percent
     */
    @JsonProperty("burning_percent")
    private String burningPercent;

    /**
     * Expiration estimate date
     */
    @JsonProperty("expiration_estimate_date")
    private String expirationEstimateDate;
}
