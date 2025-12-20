package com.orbvpn.api.domain.payload.NowPayment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

/**
 * Request body for creating a payment via NOWPayments API.
 * POST https://api.nowpayments.io/v1/payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {

    /**
     * The fiat equivalent of the price to be paid in crypto.
     * Required.
     */
    @JsonProperty("price_amount")
    private BigDecimal priceAmount;

    /**
     * The fiat currency in which the price_amount is specified (e.g., "usd", "eur").
     * Required.
     */
    @JsonProperty("price_currency")
    private String priceCurrency;

    /**
     * The cryptocurrency in which the pay_amount is specified (e.g., "btc", "eth", "ltc").
     * Required.
     */
    @JsonProperty("pay_currency")
    private String payCurrency;

    /**
     * The URL to receive IPN callbacks.
     * Optional but recommended.
     */
    @JsonProperty("ipn_callback_url")
    private String ipnCallbackUrl;

    /**
     * Your internal order ID for tracking.
     * Optional.
     */
    @JsonProperty("order_id")
    private String orderId;

    /**
     * Description of the order.
     * Optional.
     */
    @JsonProperty("order_description")
    private String orderDescription;

    /**
     * If you want to get the payment in a specific cryptocurrency, specify it here.
     * Optional.
     */
    @JsonProperty("payout_currency")
    private String payoutCurrency;

    /**
     * Case-sensitive (letters, numbers, dash).
     * Optional.
     */
    @JsonProperty("case")
    private String caseValue;

    /**
     * For fixed-rate payments.
     * Optional.
     */
    @JsonProperty("is_fixed_rate")
    private Boolean isFixedRate;

    /**
     * For fee paid by payer.
     * Optional.
     */
    @JsonProperty("is_fee_paid_by_user")
    private Boolean isFeePaidByUser;
}
