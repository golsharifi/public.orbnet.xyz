package com.orbvpn.api.domain.payload.YandexPay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Response payload from creating a Yandex Pay order.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateOrderResponse {

    @JsonProperty("data")
    private OrderData data;

    @JsonProperty("status")
    private String status;

    @JsonProperty("code")
    private Integer code;

    @JsonProperty("reasonCode")
    private String reasonCode;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderData {
        @JsonProperty("paymentUrl")
        private String paymentUrl;

        @JsonProperty("orderId")
        private String orderId;

        @JsonProperty("metadata")
        private String metadata;
    }

    /**
     * Check if the response indicates success
     */
    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status) ||
               (code != null && code == 200);
    }

    /**
     * Get payment URL from response
     */
    public String getPaymentUrl() {
        return data != null ? data.getPaymentUrl() : null;
    }

    /**
     * Get order ID from response
     */
    public String getOrderId() {
        return data != null ? data.getOrderId() : null;
    }
}
