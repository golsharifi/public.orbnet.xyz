package com.orbvpn.api.domain.payload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.payload.NowPayment.IpnCallbackPayload;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class IpnCallbackPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void builder_CreatesValidPayload() {
        IpnCallbackPayload payload = IpnCallbackPayload.builder()
                .paymentId(12345678L)
                .paymentStatus("finished")
                .payAddress("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh")
                .priceAmount(new BigDecimal("9.99"))
                .priceCurrency("usd")
                .payAmount(new BigDecimal("0.00025"))
                .payCurrency("btc")
                .actuallyPaid(new BigDecimal("0.00025"))
                .orderId("ORB-ABC12345")
                .build();

        assertEquals(12345678L, payload.getPaymentId());
        assertEquals("finished", payload.getPaymentStatus());
        assertEquals("ORB-ABC12345", payload.getOrderId());
    }

    @Test
    void isSuccessful_ReturnsTrueForFinished() {
        IpnCallbackPayload payload = new IpnCallbackPayload();
        payload.setPaymentStatus("finished");
        assertTrue(payload.isSuccessful());
    }

    @Test
    void isSuccessful_ReturnsTrueForConfirmed() {
        IpnCallbackPayload payload = new IpnCallbackPayload();
        payload.setPaymentStatus("confirmed");
        assertTrue(payload.isSuccessful());
    }

    @Test
    void isSuccessful_CaseInsensitive() {
        IpnCallbackPayload payload = new IpnCallbackPayload();
        payload.setPaymentStatus("FINISHED");
        assertTrue(payload.isSuccessful());
    }

    @Test
    void isSuccessful_ReturnsFalseForFailed() {
        IpnCallbackPayload payload = new IpnCallbackPayload();
        payload.setPaymentStatus("failed");
        assertFalse(payload.isSuccessful());
    }

    @Test
    void isPartiallyPaid_ReturnsTrueForPartiallyPaid() {
        IpnCallbackPayload payload = new IpnCallbackPayload();
        payload.setPaymentStatus("partially_paid");
        assertTrue(payload.isPartiallyPaid());
    }

    @Test
    void isPartiallyPaid_CaseInsensitive() {
        IpnCallbackPayload payload = new IpnCallbackPayload();
        payload.setPaymentStatus("PARTIALLY_PAID");
        assertTrue(payload.isPartiallyPaid());
    }

    @Test
    void isPartiallyPaid_ReturnsFalseForFinished() {
        IpnCallbackPayload payload = new IpnCallbackPayload();
        payload.setPaymentStatus("finished");
        assertFalse(payload.isPartiallyPaid());
    }

    @Test
    void isFailed_ReturnsTrueForFailed() {
        IpnCallbackPayload payload = new IpnCallbackPayload();
        payload.setPaymentStatus("failed");
        assertTrue(payload.isFailed());
    }

    @Test
    void isFailed_ReturnsTrueForExpired() {
        IpnCallbackPayload payload = new IpnCallbackPayload();
        payload.setPaymentStatus("expired");
        assertTrue(payload.isFailed());
    }

    @Test
    void isFailed_ReturnsTrueForRefunded() {
        IpnCallbackPayload payload = new IpnCallbackPayload();
        payload.setPaymentStatus("refunded");
        assertTrue(payload.isFailed());
    }

    @Test
    void isFailed_ReturnsFalseForFinished() {
        IpnCallbackPayload payload = new IpnCallbackPayload();
        payload.setPaymentStatus("finished");
        assertFalse(payload.isFailed());
    }

    @Test
    void jsonDeserialization_ParsesCorrectly() throws Exception {
        String json = """
                {
                    "payment_id": 12345678,
                    "payment_status": "finished",
                    "pay_address": "bc1qtest",
                    "price_amount": 9.99,
                    "price_currency": "usd",
                    "pay_amount": 0.00025,
                    "actually_paid": 0.00025,
                    "pay_currency": "btc",
                    "order_id": "ORB-TEST123",
                    "order_description": "Test Payment",
                    "purchase_id": "PUR123",
                    "outcome_amount": 9.50,
                    "outcome_currency": "usdt"
                }
                """;

        IpnCallbackPayload payload = objectMapper.readValue(json, IpnCallbackPayload.class);

        assertEquals(12345678L, payload.getPaymentId());
        assertEquals("finished", payload.getPaymentStatus());
        assertEquals("bc1qtest", payload.getPayAddress());
        assertEquals(new BigDecimal("9.99"), payload.getPriceAmount());
        assertEquals("usd", payload.getPriceCurrency());
        assertEquals("btc", payload.getPayCurrency());
        assertEquals("ORB-TEST123", payload.getOrderId());
        assertEquals("Test Payment", payload.getOrderDescription());
        assertEquals("PUR123", payload.getPurchaseId());
        assertEquals(new BigDecimal("9.50"), payload.getOutcomeAmount());
        assertEquals("usdt", payload.getOutcomeCurrency());
    }

    @Test
    void jsonDeserialization_IgnoresUnknownProperties() throws Exception {
        String json = """
                {
                    "payment_id": 12345678,
                    "payment_status": "finished",
                    "order_id": "ORB-TEST123",
                    "unknown_field": "should be ignored",
                    "another_unknown": 12345
                }
                """;

        IpnCallbackPayload payload = objectMapper.readValue(json, IpnCallbackPayload.class);

        assertEquals(12345678L, payload.getPaymentId());
        assertEquals("finished", payload.getPaymentStatus());
        assertEquals("ORB-TEST123", payload.getOrderId());
    }

    @Test
    void outcomeFields_CanBeSetAndRetrieved() {
        IpnCallbackPayload payload = new IpnCallbackPayload();
        payload.setOutcomeAmount(new BigDecimal("9.50"));
        payload.setOutcomeCurrency("usdt");

        assertEquals(new BigDecimal("9.50"), payload.getOutcomeAmount());
        assertEquals("usdt", payload.getOutcomeCurrency());
    }
}
