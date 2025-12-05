package com.orbvpn.api.domain.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NowPaymentTest {

    @Test
    void builder_CreatesValidEntity() {
        NowPayment payment = NowPayment.builder()
                .paymentId("12345678")
                .orderId("ORB-ABC12345")
                .paymentStatus("waiting")
                .priceAmount(new BigDecimal("9.99"))
                .priceCurrency("usd")
                .payAmount(new BigDecimal("0.00025"))
                .payCurrency("btc")
                .payAddress("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh")
                .build();

        assertEquals("12345678", payment.getPaymentId());
        assertEquals("ORB-ABC12345", payment.getOrderId());
        assertEquals("waiting", payment.getPaymentStatus());
        assertEquals(new BigDecimal("9.99"), payment.getPriceAmount());
        assertEquals("usd", payment.getPriceCurrency());
        assertEquals(new BigDecimal("0.00025"), payment.getPayAmount());
        assertEquals("btc", payment.getPayCurrency());
        assertEquals("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", payment.getPayAddress());
    }

    @Test
    void isSuccessful_ReturnsTrueForFinished() {
        NowPayment payment = new NowPayment();
        payment.setPaymentStatus("finished");
        assertTrue(payment.isSuccessful());
    }

    @Test
    void isSuccessful_ReturnsTrueForConfirmed() {
        NowPayment payment = new NowPayment();
        payment.setPaymentStatus("confirmed");
        assertTrue(payment.isSuccessful());
    }

    @Test
    void isSuccessful_ReturnsTrueForConfirmedUpperCase() {
        NowPayment payment = new NowPayment();
        payment.setPaymentStatus("CONFIRMED");
        assertTrue(payment.isSuccessful());
    }

    @Test
    void isSuccessful_ReturnsFalseForWaiting() {
        NowPayment payment = new NowPayment();
        payment.setPaymentStatus("waiting");
        assertFalse(payment.isSuccessful());
    }

    @Test
    void isPending_ReturnsTrueForWaiting() {
        NowPayment payment = new NowPayment();
        payment.setPaymentStatus("waiting");
        assertTrue(payment.isPending());
    }

    @Test
    void isPending_ReturnsTrueForConfirming() {
        NowPayment payment = new NowPayment();
        payment.setPaymentStatus("confirming");
        assertTrue(payment.isPending());
    }

    @Test
    void isPending_ReturnsTrueForSending() {
        NowPayment payment = new NowPayment();
        payment.setPaymentStatus("sending");
        assertTrue(payment.isPending());
    }

    @Test
    void isPending_ReturnsFalseForFinished() {
        NowPayment payment = new NowPayment();
        payment.setPaymentStatus("finished");
        assertFalse(payment.isPending());
    }

    @Test
    void isFailed_ReturnsTrueForFailed() {
        NowPayment payment = new NowPayment();
        payment.setPaymentStatus("failed");
        assertTrue(payment.isFailed());
    }

    @Test
    void isFailed_ReturnsTrueForExpired() {
        NowPayment payment = new NowPayment();
        payment.setPaymentStatus("expired");
        assertTrue(payment.isFailed());
    }

    @Test
    void isFailed_ReturnsTrueForRefunded() {
        NowPayment payment = new NowPayment();
        payment.setPaymentStatus("refunded");
        assertTrue(payment.isFailed());
    }

    @Test
    void isFailed_ReturnsFalseForFinished() {
        NowPayment payment = new NowPayment();
        payment.setPaymentStatus("finished");
        assertFalse(payment.isFailed());
    }

    @Test
    void setActuallyPaid_TracksPartialPayment() {
        NowPayment payment = NowPayment.builder()
                .payAmount(new BigDecimal("0.001"))
                .paymentStatus("partially_paid")
                .build();

        payment.setActuallyPaid(new BigDecimal("0.0005"));

        assertEquals(new BigDecimal("0.0005"), payment.getActuallyPaid());
        assertEquals(new BigDecimal("0.001"), payment.getPayAmount());
    }

    @Test
    void outcomeFields_CanBeSetAndRetrieved() {
        NowPayment payment = new NowPayment();
        payment.setOutcomeAmount(new BigDecimal("9.50"));
        payment.setOutcomeCurrency("usdt");

        assertEquals(new BigDecimal("9.50"), payment.getOutcomeAmount());
        assertEquals("usdt", payment.getOutcomeCurrency());
    }

    @Test
    void expiresAt_CanBeSetAndRetrieved() {
        NowPayment payment = new NowPayment();
        LocalDateTime expiration = LocalDateTime.of(2024, 1, 15, 12, 30);
        payment.setExpiresAt(expiration);

        assertEquals(expiration, payment.getExpiresAt());
    }

    @Test
    void defaultValues_AreNull() {
        NowPayment payment = new NowPayment();

        assertNull(payment.getId());
        assertNull(payment.getPaymentId());
        assertNull(payment.getOrderId());
        assertNull(payment.getPaymentStatus());
        assertNull(payment.getPriceAmount());
        assertNull(payment.getPayAmount());
        assertNull(payment.getPayAddress());
        assertNull(payment.getActuallyPaid());
    }
}
