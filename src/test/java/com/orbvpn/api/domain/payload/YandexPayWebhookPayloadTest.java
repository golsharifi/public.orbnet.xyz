package com.orbvpn.api.domain.payload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.payload.YandexPay.WebhookPayload;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class YandexPayWebhookPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void builder_CreatesValidPayload() {
        WebhookPayload payload = WebhookPayload.builder()
                .event(WebhookPayload.EVENT_ORDER_PAID)
                .orderId("ORB-YP-ABC12345")
                .merchantId("merchant-123")
                .operationId("op-456")
                .status(WebhookPayload.STATUS_CONFIRMED)
                .amount(new BigDecimal("999.99"))
                .currency("RUB")
                .paymentMethod("CARD")
                .build();

        assertEquals(WebhookPayload.EVENT_ORDER_PAID, payload.getEvent());
        assertEquals("ORB-YP-ABC12345", payload.getOrderId());
        assertEquals(WebhookPayload.STATUS_CONFIRMED, payload.getStatus());
        assertEquals(new BigDecimal("999.99"), payload.getAmount());
        assertEquals("RUB", payload.getCurrency());
    }

    @Test
    void isSuccessfulPayment_ReturnsTrueForOrderPaid() {
        WebhookPayload payload = new WebhookPayload();
        payload.setEvent(WebhookPayload.EVENT_ORDER_PAID);
        assertTrue(payload.isSuccessfulPayment());
    }

    @Test
    void isSuccessfulPayment_ReturnsTrueForOrderCaptured() {
        WebhookPayload payload = new WebhookPayload();
        payload.setEvent(WebhookPayload.EVENT_ORDER_CAPTURED);
        assertTrue(payload.isSuccessfulPayment());
    }

    @Test
    void isSuccessfulPayment_ReturnsTrueForCapturedStatus() {
        WebhookPayload payload = new WebhookPayload();
        payload.setStatus(WebhookPayload.STATUS_CAPTURED);
        assertTrue(payload.isSuccessfulPayment());
    }

    @Test
    void isSuccessfulPayment_ReturnsTrueForConfirmedStatus() {
        WebhookPayload payload = new WebhookPayload();
        payload.setStatus(WebhookPayload.STATUS_CONFIRMED);
        assertTrue(payload.isSuccessfulPayment());
    }

    @Test
    void isSuccessfulPayment_CaseInsensitive() {
        WebhookPayload payload = new WebhookPayload();
        payload.setStatus("confirmed");
        assertTrue(payload.isSuccessfulPayment());
    }

    @Test
    void isSuccessfulPayment_ReturnsFalseForFailed() {
        WebhookPayload payload = new WebhookPayload();
        payload.setStatus(WebhookPayload.STATUS_FAILED);
        assertFalse(payload.isSuccessfulPayment());
    }

    @Test
    void isFailedPayment_ReturnsTrueForOrderFailed() {
        WebhookPayload payload = new WebhookPayload();
        payload.setEvent(WebhookPayload.EVENT_ORDER_FAILED);
        assertTrue(payload.isFailedPayment());
    }

    @Test
    void isFailedPayment_ReturnsTrueForOrderCancelled() {
        WebhookPayload payload = new WebhookPayload();
        payload.setEvent(WebhookPayload.EVENT_ORDER_CANCELLED);
        assertTrue(payload.isFailedPayment());
    }

    @Test
    void isFailedPayment_ReturnsTrueForFailedStatus() {
        WebhookPayload payload = new WebhookPayload();
        payload.setStatus(WebhookPayload.STATUS_FAILED);
        assertTrue(payload.isFailedPayment());
    }

    @Test
    void isFailedPayment_ReturnsTrueForCancelledStatus() {
        WebhookPayload payload = new WebhookPayload();
        payload.setStatus(WebhookPayload.STATUS_CANCELLED);
        assertTrue(payload.isFailedPayment());
    }

    @Test
    void isFailedPayment_ReturnsFalseForConfirmed() {
        WebhookPayload payload = new WebhookPayload();
        payload.setStatus(WebhookPayload.STATUS_CONFIRMED);
        assertFalse(payload.isFailedPayment());
    }

    @Test
    void isRefundEvent_ReturnsTrueForOrderRefunded() {
        WebhookPayload payload = new WebhookPayload();
        payload.setEvent(WebhookPayload.EVENT_ORDER_REFUNDED);
        assertTrue(payload.isRefundEvent());
    }

    @Test
    void isRefundEvent_ReturnsTrueForRefundedStatus() {
        WebhookPayload payload = new WebhookPayload();
        payload.setStatus(WebhookPayload.STATUS_REFUNDED);
        assertTrue(payload.isRefundEvent());
    }

    @Test
    void isRefundEvent_ReturnsFalseForConfirmed() {
        WebhookPayload payload = new WebhookPayload();
        payload.setStatus(WebhookPayload.STATUS_CONFIRMED);
        assertFalse(payload.isRefundEvent());
    }

    @Test
    void eventConstants_AreCorrect() {
        assertEquals("ORDER_CREATED", WebhookPayload.EVENT_ORDER_CREATED);
        assertEquals("ORDER_PAID", WebhookPayload.EVENT_ORDER_PAID);
        assertEquals("ORDER_CAPTURED", WebhookPayload.EVENT_ORDER_CAPTURED);
        assertEquals("ORDER_CANCELLED", WebhookPayload.EVENT_ORDER_CANCELLED);
        assertEquals("ORDER_REFUNDED", WebhookPayload.EVENT_ORDER_REFUNDED);
        assertEquals("ORDER_FAILED", WebhookPayload.EVENT_ORDER_FAILED);
    }

    @Test
    void statusConstants_AreCorrect() {
        assertEquals("PENDING", WebhookPayload.STATUS_PENDING);
        assertEquals("AUTHORIZED", WebhookPayload.STATUS_AUTHORIZED);
        assertEquals("CAPTURED", WebhookPayload.STATUS_CAPTURED);
        assertEquals("CONFIRMED", WebhookPayload.STATUS_CONFIRMED);
        assertEquals("CANCELLED", WebhookPayload.STATUS_CANCELLED);
        assertEquals("REFUNDED", WebhookPayload.STATUS_REFUNDED);
        assertEquals("FAILED", WebhookPayload.STATUS_FAILED);
    }

    @Test
    void jsonDeserialization_ParsesCorrectly() throws Exception {
        String json = """
                {
                    "event": "ORDER_PAID",
                    "orderId": "ORB-YP-TEST123",
                    "merchantId": "merchant-abc",
                    "operationId": "op-123",
                    "status": "CONFIRMED",
                    "amount": 1500.00,
                    "currency": "RUB",
                    "paymentMethod": "CARD",
                    "reason": null,
                    "reasonCode": null
                }
                """;

        WebhookPayload payload = objectMapper.readValue(json, WebhookPayload.class);

        assertEquals("ORDER_PAID", payload.getEvent());
        assertEquals("ORB-YP-TEST123", payload.getOrderId());
        assertEquals("merchant-abc", payload.getMerchantId());
        assertEquals("op-123", payload.getOperationId());
        assertEquals("CONFIRMED", payload.getStatus());
        assertEquals(new BigDecimal("1500.00"), payload.getAmount());
        assertEquals("RUB", payload.getCurrency());
        assertEquals("CARD", payload.getPaymentMethod());
    }

    @Test
    void jsonDeserialization_IgnoresUnknownProperties() throws Exception {
        String json = """
                {
                    "event": "ORDER_PAID",
                    "orderId": "ORB-YP-TEST123",
                    "status": "CONFIRMED",
                    "unknown_field": "should be ignored",
                    "another_unknown": 12345
                }
                """;

        WebhookPayload payload = objectMapper.readValue(json, WebhookPayload.class);

        assertEquals("ORDER_PAID", payload.getEvent());
        assertEquals("ORB-YP-TEST123", payload.getOrderId());
        assertEquals("CONFIRMED", payload.getStatus());
    }

    @Test
    void reason_CanBeSetAndRetrieved() {
        WebhookPayload payload = new WebhookPayload();
        payload.setReason("Insufficient funds");
        payload.setReasonCode("INSUFFICIENT_FUNDS");

        assertEquals("Insufficient funds", payload.getReason());
        assertEquals("INSUFFICIENT_FUNDS", payload.getReasonCode());
    }
}
