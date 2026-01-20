package com.orbvpn.api.domain.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ProcessedCoinPaymentWebhookTest {

    @Test
    void builder_CreatesValidEntity() {
        ProcessedCoinPaymentWebhook webhook = ProcessedCoinPaymentWebhook.builder()
                .ipnId("coinpayment_123_txn456_100")
                .paymentId(123L)
                .txnId("txn456")
                .status(100)
                .amount(BigDecimal.valueOf(0.005))
                .successful(true)
                .build();

        assertEquals("coinpayment_123_txn456_100", webhook.getIpnId());
        assertEquals(123L, webhook.getPaymentId());
        assertEquals("txn456", webhook.getTxnId());
        assertEquals(100, webhook.getStatus());
        assertEquals(BigDecimal.valueOf(0.005), webhook.getAmount());
        assertTrue(webhook.isSuccessful());
    }

    @Test
    void generateIpnId_CreatesCorrectFormat_WithTxnId() {
        String ipnId = ProcessedCoinPaymentWebhook.generateIpnId(123L, "txn456", 100);
        assertEquals("coinpayment_123_txn456_100", ipnId);
    }

    @Test
    void generateIpnId_CreatesCorrectFormat_WithNullTxnId() {
        String ipnId = ProcessedCoinPaymentWebhook.generateIpnId(123L, null, 100);
        assertEquals("coinpayment_123_callback_100", ipnId);
    }

    @Test
    void generateIpnId_HandlesNegativeStatus() {
        String ipnId = ProcessedCoinPaymentWebhook.generateIpnId(123L, "txn456", -1);
        assertEquals("coinpayment_123_txn456_-1", ipnId);
    }

    @Test
    void generateIpnId_CreatesDifferentIds_ForDifferentStatuses() {
        String ipnId1 = ProcessedCoinPaymentWebhook.generateIpnId(123L, "txn456", 0);
        String ipnId2 = ProcessedCoinPaymentWebhook.generateIpnId(123L, "txn456", 100);

        assertNotEquals(ipnId1, ipnId2);
    }

    @Test
    void generateIpnId_CreatesDifferentIds_ForDifferentPayments() {
        String ipnId1 = ProcessedCoinPaymentWebhook.generateIpnId(123L, "txn456", 100);
        String ipnId2 = ProcessedCoinPaymentWebhook.generateIpnId(456L, "txn456", 100);

        assertNotEquals(ipnId1, ipnId2);
    }

    @Test
    void setErrorMessage_UpdatesMessage() {
        ProcessedCoinPaymentWebhook webhook = ProcessedCoinPaymentWebhook.builder()
                .ipnId("test_ipn_id")
                .paymentId(1L)
                .build();

        webhook.setErrorMessage("Insufficient amount");
        assertEquals("Insufficient amount", webhook.getErrorMessage());
    }

    @Test
    void setSuccessful_UpdatesFlag() {
        ProcessedCoinPaymentWebhook webhook = ProcessedCoinPaymentWebhook.builder()
                .ipnId("test_ipn_id")
                .paymentId(1L)
                .successful(false)
                .build();

        assertFalse(webhook.isSuccessful());

        webhook.setSuccessful(true);
        assertTrue(webhook.isSuccessful());
    }

    @Test
    void defaultValues_AreNull() {
        ProcessedCoinPaymentWebhook webhook = new ProcessedCoinPaymentWebhook();

        assertNull(webhook.getId());
        assertNull(webhook.getIpnId());
        assertNull(webhook.getPaymentId());
        assertNull(webhook.getTxnId());
        assertNull(webhook.getStatus());
        assertNull(webhook.getAmount());
        assertNull(webhook.getProcessedAt());
        assertNull(webhook.getErrorMessage());
    }

    @Test
    void allArgsConstructor_SetsAllFields() {
        LocalDateTime now = LocalDateTime.now();
        ProcessedCoinPaymentWebhook webhook = new ProcessedCoinPaymentWebhook(
                1L,
                "ipn_id",
                100L,
                "txn123",
                100,
                BigDecimal.valueOf(0.005),
                now,
                true,
                null
        );

        assertEquals(1L, webhook.getId());
        assertEquals("ipn_id", webhook.getIpnId());
        assertEquals(100L, webhook.getPaymentId());
        assertEquals("txn123", webhook.getTxnId());
        assertEquals(100, webhook.getStatus());
        assertEquals(BigDecimal.valueOf(0.005), webhook.getAmount());
        assertEquals(now, webhook.getProcessedAt());
        assertTrue(webhook.isSuccessful());
        assertNull(webhook.getErrorMessage());
    }

    @Test
    void setProcessedAt_UpdatesTimestamp() {
        ProcessedCoinPaymentWebhook webhook = ProcessedCoinPaymentWebhook.builder()
                .ipnId("test_ipn_id")
                .paymentId(1L)
                .build();

        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        webhook.setProcessedAt(timestamp);

        assertEquals(timestamp, webhook.getProcessedAt());
    }
}
