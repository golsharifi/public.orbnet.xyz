package com.orbvpn.api.domain.payload.YandexPay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * Request payload for creating a Yandex Pay order.
 * POST /api/merchant/v1/orders
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateOrderRequest {

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("currencyCode")
    @Builder.Default
    private String currencyCode = "RUB";

    @JsonProperty("availablePaymentMethods")
    @Builder.Default
    private List<String> availablePaymentMethods = List.of("CARD");

    @JsonProperty("redirectUrls")
    private RedirectUrls redirectUrls;

    @JsonProperty("cart")
    private Cart cart;

    /**
     * Time to live in seconds (default 30 minutes)
     */
    @JsonProperty("ttl")
    @Builder.Default
    private Integer ttl = 1800;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RedirectUrls {
        @JsonProperty("onSuccess")
        private String onSuccess;

        @JsonProperty("onError")
        private String onError;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cart {
        @JsonProperty("total")
        private Total total;

        @JsonProperty("items")
        private List<CartItem> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Total {
        @JsonProperty("amount")
        private String amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItem {
        @JsonProperty("productId")
        private String productId;

        @JsonProperty("title")
        private String title;

        @JsonProperty("quantity")
        private Quantity quantity;

        @JsonProperty("total")
        private String total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Quantity {
        @JsonProperty("count")
        @Builder.Default
        private Integer count = 1;
    }
}
