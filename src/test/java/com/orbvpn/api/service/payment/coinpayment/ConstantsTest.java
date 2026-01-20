package com.orbvpn.api.service.payment.coinpayment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ConstantsTest {

    @Test
    void statusConstants_HaveCorrectValues() {
        assertEquals(-1, Constants.STATUS_CANCELLED);
        assertEquals(-2, Constants.STATUS_ERROR);
        assertEquals(0, Constants.STATUS_WAITING);
        assertEquals(1, Constants.STATUS_CONFIRMING);
        assertEquals(2, Constants.STATUS_COMPLETE);
        assertEquals(3, Constants.STATUS_QUEUED);
        assertEquals(100, Constants.STATUS_COMPLETE_THRESHOLD);
    }

    @Test
    void isPaymentComplete_ReturnsTrue_ForStatus100OrHigher() {
        assertTrue(Constants.isPaymentComplete(100));
        assertTrue(Constants.isPaymentComplete(101));
        assertTrue(Constants.isPaymentComplete(200));
        assertTrue(Constants.isPaymentComplete(1000));
    }

    @Test
    void isPaymentComplete_ReturnsTrue_ForStatus2() {
        assertTrue(Constants.isPaymentComplete(2));
    }

    @ParameterizedTest
    @ValueSource(ints = {-2, -1, 0, 1, 3, 50, 99})
    void isPaymentComplete_ReturnsFalse_ForIncompleteStatus(int status) {
        assertFalse(Constants.isPaymentComplete(status));
    }

    @Test
    void isPaymentFailed_ReturnsTrue_ForNegativeStatus() {
        assertTrue(Constants.isPaymentFailed(-1));
        assertTrue(Constants.isPaymentFailed(-2));
        assertTrue(Constants.isPaymentFailed(-100));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 100, 200})
    void isPaymentFailed_ReturnsFalse_ForNonNegativeStatus(int status) {
        assertFalse(Constants.isPaymentFailed(status));
    }

    @Test
    void isPaymentPending_ReturnsTrue_ForWaitingStatus() {
        assertTrue(Constants.isPaymentPending(0));
    }

    @Test
    void isPaymentPending_ReturnsTrue_ForConfirmingStatus() {
        assertTrue(Constants.isPaymentPending(1));
    }

    @Test
    void isPaymentPending_ReturnsFalse_ForCompleteStatus() {
        assertFalse(Constants.isPaymentPending(2));
        assertFalse(Constants.isPaymentPending(100));
        assertFalse(Constants.isPaymentPending(200));
    }

    @Test
    void isPaymentPending_ReturnsFalse_ForFailedStatus() {
        assertFalse(Constants.isPaymentPending(-1));
        assertFalse(Constants.isPaymentPending(-2));
    }

    @Test
    void statusConstants_AreDistinct() {
        // Ensure all status constants are unique
        int[] statuses = {
            Constants.STATUS_CANCELLED,
            Constants.STATUS_ERROR,
            Constants.STATUS_WAITING,
            Constants.STATUS_CONFIRMING,
            Constants.STATUS_COMPLETE,
            Constants.STATUS_QUEUED
        };

        for (int i = 0; i < statuses.length; i++) {
            for (int j = i + 1; j < statuses.length; j++) {
                assertNotEquals(statuses[i], statuses[j],
                    "Status constants should be unique");
            }
        }
    }

    @Test
    void hmacAlgorithm_IsCorrect() {
        assertEquals("HmacSHA512", Constants.HMAC_SHA_512);
    }

    @Test
    void coinsApiUrl_IsCorrect() {
        assertEquals("https://www.coinpayments.net/api.php", Constants.COINS_API_URL);
    }

    @Test
    void successMessage_IsCorrect() {
        assertEquals("ok", Constants.SUCCESS_MESSAGE);
    }
}
