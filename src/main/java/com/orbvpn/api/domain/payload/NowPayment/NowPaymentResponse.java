package com.orbvpn.api.domain.payload.NowPayment;

import lombok.*;

import java.math.BigDecimal;

/**
 * GraphQL response DTO for NOWPayments payment creation.
 * This is returned to the client after creating a payment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NowPaymentResponse {

    /**
     * Internal database ID
     */
    private Long id;

    /**
     * NOWPayments payment ID
     */
    private String paymentId;

    /**
     * Payment status
     */
    private String status;

    /**
     * Cryptocurrency deposit address
     */
    private String payAddress;

    /**
     * Amount to pay in cryptocurrency
     */
    private BigDecimal payAmount;

    /**
     * Cryptocurrency code (e.g., "btc")
     */
    private String payCurrency;

    /**
     * Price in fiat
     */
    private BigDecimal priceAmount;

    /**
     * Fiat currency (e.g., "usd")
     */
    private String priceCurrency;

    /**
     * Order ID for tracking
     */
    private String orderId;

    /**
     * When payment expires
     */
    private String expiresAt;

    /**
     * Error message if payment creation failed
     */
    private String error;

    /**
     * Network for the payment (e.g., "btc", "eth", "bsc")
     */
    private String network;

    /**
     * Extra ID needed for some currencies (like XRP destination tag)
     */
    private String payinExtraId;
}
