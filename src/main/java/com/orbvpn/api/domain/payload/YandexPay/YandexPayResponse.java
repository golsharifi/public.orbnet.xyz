package com.orbvpn.api.domain.payload.YandexPay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;

/**
 * Response DTO returned to GraphQL/REST clients for Yandex Pay payments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class YandexPayResponse {

    /**
     * Internal payment ID
     */
    private Long id;

    /**
     * Yandex Pay order ID
     */
    private String yandexOrderId;

    /**
     * Internal order ID
     */
    private String orderId;

    /**
     * Payment status
     */
    private String status;

    /**
     * Payment URL for user redirection
     */
    private String paymentUrl;

    /**
     * Amount in RUB
     */
    private BigDecimal amount;

    /**
     * Currency (RUB)
     */
    @Builder.Default
    private String currency = "RUB";

    /**
     * Expiration time as ISO string
     */
    private String expiresAt;

    /**
     * Error message if failed
     */
    private String error;

    /**
     * Check if response indicates success
     */
    public boolean isSuccess() {
        return error == null || error.isEmpty();
    }
}
