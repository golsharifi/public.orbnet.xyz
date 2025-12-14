package com.orbvpn.api.domain.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class YandexPaymentTest {

    @Test
    void builder_CreatesValidEntity() {
        YandexPayment payment = YandexPayment.builder()
                .yandexOrderId("YP-123456789")
                .orderId("ORB-YP-ABC12345")
                .paymentStatus(YandexPayment.STATUS_PENDING)
                .amount(new BigDecimal("999.99"))
                .currency("RUB")
                .paymentUrl("https://pay.yandex.ru/checkout/...")
                .description("OrbVPN Subscription")
                .build();

        assertEquals("YP-123456789", payment.getYandexOrderId());
        assertEquals("ORB-YP-ABC12345", payment.getOrderId());
        assertEquals(YandexPayment.STATUS_PENDING, payment.getPaymentStatus());
        assertEquals(new BigDecimal("999.99"), payment.getAmount());
        assertEquals("RUB", payment.getCurrency());
        assertEquals("https://pay.yandex.ru/checkout/...", payment.getPaymentUrl());
        assertEquals("OrbVPN Subscription", payment.getDescription());
    }

    @Test
    void isSuccessful_ReturnsTrueForCaptured() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentStatus(YandexPayment.STATUS_CAPTURED);
        assertTrue(payment.isSuccessful());
    }

    @Test
    void isSuccessful_ReturnsTrueForConfirmed() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentStatus(YandexPayment.STATUS_CONFIRMED);
        assertTrue(payment.isSuccessful());
    }

    @Test
    void isSuccessful_CaseInsensitive() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentStatus("confirmed");
        assertTrue(payment.isSuccessful());
    }

    @Test
    void isSuccessful_ReturnsFalseForPending() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentStatus(YandexPayment.STATUS_PENDING);
        assertFalse(payment.isSuccessful());
    }

    @Test
    void isPending_ReturnsTrueForPending() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentStatus(YandexPayment.STATUS_PENDING);
        assertTrue(payment.isPending());
    }

    @Test
    void isPending_ReturnsTrueForAuthorized() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentStatus(YandexPayment.STATUS_AUTHORIZED);
        assertTrue(payment.isPending());
    }

    @Test
    void isPending_ReturnsFalseForConfirmed() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentStatus(YandexPayment.STATUS_CONFIRMED);
        assertFalse(payment.isPending());
    }

    @Test
    void isFailed_ReturnsTrueForFailed() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentStatus(YandexPayment.STATUS_FAILED);
        assertTrue(payment.isFailed());
    }

    @Test
    void isFailed_ReturnsTrueForCancelled() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentStatus(YandexPayment.STATUS_CANCELLED);
        assertTrue(payment.isFailed());
    }

    @Test
    void isFailed_ReturnsFalseForConfirmed() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentStatus(YandexPayment.STATUS_CONFIRMED);
        assertFalse(payment.isFailed());
    }

    @Test
    void isRefunded_ReturnsTrueForRefunded() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentStatus(YandexPayment.STATUS_REFUNDED);
        assertTrue(payment.isRefunded());
    }

    @Test
    void isRefunded_ReturnsFalseForConfirmed() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentStatus(YandexPayment.STATUS_CONFIRMED);
        assertFalse(payment.isRefunded());
    }

    @Test
    void markSuccess_UpdatesStatus() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentStatus(YandexPayment.STATUS_PENDING);

        payment.markSuccess();

        assertEquals(YandexPayment.STATUS_CONFIRMED, payment.getPaymentStatus());
        assertTrue(payment.isSuccessful());
    }

    @Test
    void markFailed_UpdatesStatusAndErrorMessage() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentStatus(YandexPayment.STATUS_PENDING);

        payment.markFailed("Card declined");

        assertEquals(YandexPayment.STATUS_FAILED, payment.getPaymentStatus());
        assertEquals("Card declined", payment.getErrorMessage());
        assertTrue(payment.isFailed());
    }

    @Test
    void markCancelled_UpdatesStatus() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentStatus(YandexPayment.STATUS_PENDING);

        payment.markCancelled();

        assertEquals(YandexPayment.STATUS_CANCELLED, payment.getPaymentStatus());
        assertTrue(payment.isFailed());
    }

    @Test
    void markRefunded_UpdatesStatus() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentStatus(YandexPayment.STATUS_CONFIRMED);

        payment.markRefunded();

        assertEquals(YandexPayment.STATUS_REFUNDED, payment.getPaymentStatus());
        assertTrue(payment.isRefunded());
    }

    @Test
    void statusConstants_AreCorrect() {
        assertEquals("PENDING", YandexPayment.STATUS_PENDING);
        assertEquals("AUTHORIZED", YandexPayment.STATUS_AUTHORIZED);
        assertEquals("CAPTURED", YandexPayment.STATUS_CAPTURED);
        assertEquals("CONFIRMED", YandexPayment.STATUS_CONFIRMED);
        assertEquals("CANCELLED", YandexPayment.STATUS_CANCELLED);
        assertEquals("REFUNDED", YandexPayment.STATUS_REFUNDED);
        assertEquals("FAILED", YandexPayment.STATUS_FAILED);
    }

    @Test
    void expiresAt_CanBeSetAndRetrieved() {
        YandexPayment payment = new YandexPayment();
        LocalDateTime expiration = LocalDateTime.of(2024, 1, 15, 12, 30);
        payment.setExpiresAt(expiration);

        assertEquals(expiration, payment.getExpiresAt());
    }

    @Test
    void operationId_CanBeSetAndRetrieved() {
        YandexPayment payment = new YandexPayment();
        payment.setOperationId("OP-123456");
        assertEquals("OP-123456", payment.getOperationId());
    }

    @Test
    void paymentMethod_CanBeSetAndRetrieved() {
        YandexPayment payment = new YandexPayment();
        payment.setPaymentMethod("CARD");
        assertEquals("CARD", payment.getPaymentMethod());
    }

    @Test
    void defaultCurrency_IsRUB() {
        YandexPayment payment = YandexPayment.builder().build();
        assertEquals("RUB", payment.getCurrency());
    }

    @Test
    void defaultValues_AreNull() {
        YandexPayment payment = new YandexPayment();

        assertNull(payment.getId());
        assertNull(payment.getYandexOrderId());
        assertNull(payment.getOrderId());
        assertNull(payment.getPaymentStatus());
        assertNull(payment.getAmount());
        assertNull(payment.getPaymentUrl());
        assertNull(payment.getDescription());
        assertNull(payment.getOperationId());
        assertNull(payment.getErrorMessage());
    }
}
