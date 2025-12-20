package com.orbvpn.api.controller;

import com.orbvpn.api.domain.entity.CoinPaymentCallback;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.ProcessedCoinPaymentWebhook;
import com.orbvpn.api.repository.ProcessedCoinPaymentWebhookRepository;
import com.orbvpn.api.service.payment.PaymentService;
import com.orbvpn.api.service.payment.coinpayment.CoinPaymentBaseService;
import com.orbvpn.api.service.payment.coinpayment.CoinPaymentService;
import com.orbvpn.api.service.webhook.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoinPaymentWebhookControllerTest {

    @Mock
    private CoinPaymentService coinPaymentService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private WebhookService webhookService;
    @Mock
    private ProcessedCoinPaymentWebhookRepository webhookRepository;

    private CoinPaymentWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new CoinPaymentWebhookController(
                coinPaymentService,
                paymentService,
                webhookService,
                webhookRepository
        );
    }

    @Test
    void handleIpnNotification_ReturnsBadRequest_WhenHmacMissing() {
        Map<String, String> payload = new HashMap<>();
        payload.put("status", "100");
        payload.put("amount", "0.005");

        ResponseEntity<String> response = controller.handleIpnNotification(
                1L, payload, null, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("HMAC header required", response.getBody());
    }

    @Test
    void handleIpnNotification_ReturnsBadRequest_WhenPaymentNotFound() {
        Map<String, String> payload = new HashMap<>();
        payload.put("status", "100");
        payload.put("amount", "0.005");

        when(coinPaymentService.getCallbackPayment(1L)).thenReturn(null);

        ResponseEntity<String> response = controller.handleIpnNotification(
                1L, payload, "test_hmac", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Payment not found", response.getBody());
    }

    @Test
    void handleIpnNotification_ReturnsOk_WhenAlreadyProcessed() {
        Map<String, String> payload = new HashMap<>();
        payload.put("status", "100");
        payload.put("amount", "0.005");
        payload.put("txn_id", "txn123");

        CoinPaymentCallback mockPayment = mock(CoinPaymentCallback.class);
        Payment mockBasePayment = mock(Payment.class);
        when(mockPayment.getPayment()).thenReturn(mockBasePayment);
        when(coinPaymentService.getCallbackPayment(1L)).thenReturn(mockPayment);
        when(coinPaymentService.getIpnSecret()).thenReturn("test_secret");

        // Mock that IPN was already processed
        when(webhookRepository.existsByIpnId(anyString())).thenReturn(true);

        // We need a valid HMAC for this test - let's compute it
        String expectedPayloadString = "amount=0.005&status=100&txn_id=txn123";
        String validHmac = CoinPaymentBaseService.buildHmacSignature(
                expectedPayloadString.replaceAll("@", "%40").replace(' ', '+'),
                "test_secret"
        );

        ResponseEntity<String> response = controller.handleIpnNotification(
                1L, payload, validHmac, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("IPN already processed", response.getBody());
    }

    @Test
    void handleIpnNotification_ReturnsBadRequest_WhenInsufficientAmount() {
        Map<String, String> payload = new HashMap<>();
        payload.put("status", "100");
        payload.put("amount", "0.001");
        payload.put("txn_id", "txn123");

        CoinPaymentCallback mockPayment = mock(CoinPaymentCallback.class);
        Payment mockBasePayment = mock(Payment.class);
        when(mockPayment.getPayment()).thenReturn(mockBasePayment);
        when(mockPayment.getCoinAmount()).thenReturn(BigDecimal.valueOf(0.005)); // Expected more than received
        when(coinPaymentService.getCallbackPayment(1L)).thenReturn(mockPayment);
        when(coinPaymentService.getIpnSecret()).thenReturn("test_secret");
        when(webhookRepository.existsByIpnId(anyString())).thenReturn(false);

        String expectedPayloadString = "amount=0.001&status=100&txn_id=txn123";
        String validHmac = CoinPaymentBaseService.buildHmacSignature(
                expectedPayloadString.replaceAll("@", "%40").replace(' ', '+'),
                "test_secret"
        );

        ResponseEntity<String> response = controller.handleIpnNotification(
                1L, payload, validHmac, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Insufficient payment amount", response.getBody());

        verify(webhookRepository).save(any(ProcessedCoinPaymentWebhook.class));
    }

    @Test
    void handleIpnNotification_FulfillsPayment_WhenStatusComplete() {
        Map<String, String> payload = new HashMap<>();
        payload.put("status", "100");
        payload.put("amount", "0.005");
        payload.put("txn_id", "txn123");

        CoinPaymentCallback mockPayment = mock(CoinPaymentCallback.class);
        Payment mockBasePayment = mock(Payment.class);
        when(mockPayment.getPayment()).thenReturn(mockBasePayment);
        when(mockPayment.getCoinAmount()).thenReturn(BigDecimal.valueOf(0.005));
        when(coinPaymentService.getCallbackPayment(1L)).thenReturn(mockPayment);
        when(coinPaymentService.getIpnSecret()).thenReturn("test_secret");
        when(webhookRepository.existsByIpnId(anyString())).thenReturn(false);

        String expectedPayloadString = "amount=0.005&status=100&txn_id=txn123";
        String validHmac = CoinPaymentBaseService.buildHmacSignature(
                expectedPayloadString.replaceAll("@", "%40").replace(' ', '+'),
                "test_secret"
        );

        ResponseEntity<String> response = controller.handleIpnNotification(
                1L, payload, validHmac, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("IPN processed", response.getBody());

        verify(paymentService).fullFillPayment(mockBasePayment);
        verify(webhookService).processWebhook(eq("PAYMENT_COMPLETED"), anyMap());
        verify(webhookRepository).save(any(ProcessedCoinPaymentWebhook.class));
    }

    @Test
    void handleIpnNotification_FulfillsPayment_WhenStatus2() {
        Map<String, String> payload = new HashMap<>();
        payload.put("status", "2");
        payload.put("amount", "0.005");
        payload.put("txn_id", "txn123");

        CoinPaymentCallback mockPayment = mock(CoinPaymentCallback.class);
        Payment mockBasePayment = mock(Payment.class);
        when(mockPayment.getPayment()).thenReturn(mockBasePayment);
        when(mockPayment.getCoinAmount()).thenReturn(BigDecimal.valueOf(0.005));
        when(coinPaymentService.getCallbackPayment(1L)).thenReturn(mockPayment);
        when(coinPaymentService.getIpnSecret()).thenReturn("test_secret");
        when(webhookRepository.existsByIpnId(anyString())).thenReturn(false);

        String expectedPayloadString = "amount=0.005&status=2&txn_id=txn123";
        String validHmac = CoinPaymentBaseService.buildHmacSignature(
                expectedPayloadString.replaceAll("@", "%40").replace(' ', '+'),
                "test_secret"
        );

        ResponseEntity<String> response = controller.handleIpnNotification(
                1L, payload, validHmac, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(paymentService).fullFillPayment(mockBasePayment);
    }

    @Test
    void handleIpnNotification_DoesNotFulfill_WhenStatusPending() {
        Map<String, String> payload = new HashMap<>();
        payload.put("status", "1"); // Confirming
        payload.put("amount", "0.005");
        payload.put("txn_id", "txn123");

        CoinPaymentCallback mockPayment = mock(CoinPaymentCallback.class);
        Payment mockBasePayment = mock(Payment.class);
        when(mockPayment.getPayment()).thenReturn(mockBasePayment);
        when(coinPaymentService.getCallbackPayment(1L)).thenReturn(mockPayment);
        when(coinPaymentService.getIpnSecret()).thenReturn("test_secret");
        when(webhookRepository.existsByIpnId(anyString())).thenReturn(false);

        String expectedPayloadString = "amount=0.005&status=1&txn_id=txn123";
        String validHmac = CoinPaymentBaseService.buildHmacSignature(
                expectedPayloadString.replaceAll("@", "%40").replace(' ', '+'),
                "test_secret"
        );

        ResponseEntity<String> response = controller.handleIpnNotification(
                1L, payload, validHmac, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleIpnNotification_DoesNotFulfill_WhenStatusFailed() {
        Map<String, String> payload = new HashMap<>();
        payload.put("status", "-1"); // Cancelled
        payload.put("amount", "0.005");
        payload.put("txn_id", "txn123");

        CoinPaymentCallback mockPayment = mock(CoinPaymentCallback.class);
        Payment mockBasePayment = mock(Payment.class);
        when(mockPayment.getPayment()).thenReturn(mockBasePayment);
        when(coinPaymentService.getCallbackPayment(1L)).thenReturn(mockPayment);
        when(coinPaymentService.getIpnSecret()).thenReturn("test_secret");
        when(webhookRepository.existsByIpnId(anyString())).thenReturn(false);

        String expectedPayloadString = "amount=0.005&status=-1&txn_id=txn123";
        String validHmac = CoinPaymentBaseService.buildHmacSignature(
                expectedPayloadString.replaceAll("@", "%40").replace(' ', '+'),
                "test_secret"
        );

        ResponseEntity<String> response = controller.handleIpnNotification(
                1L, payload, validHmac, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleIpnNotification_AcceptsLowercaseHmacHeader() {
        Map<String, String> payload = new HashMap<>();
        payload.put("status", "100");
        payload.put("amount", "0.005");
        payload.put("txn_id", "txn123");

        CoinPaymentCallback mockPayment = mock(CoinPaymentCallback.class);
        Payment mockBasePayment = mock(Payment.class);
        when(mockPayment.getPayment()).thenReturn(mockBasePayment);
        when(mockPayment.getCoinAmount()).thenReturn(BigDecimal.valueOf(0.005));
        when(coinPaymentService.getCallbackPayment(1L)).thenReturn(mockPayment);
        when(coinPaymentService.getIpnSecret()).thenReturn("test_secret");
        when(webhookRepository.existsByIpnId(anyString())).thenReturn(false);

        String expectedPayloadString = "amount=0.005&status=100&txn_id=txn123";
        String validHmac = CoinPaymentBaseService.buildHmacSignature(
                expectedPayloadString.replaceAll("@", "%40").replace(' ', '+'),
                "test_secret"
        );

        // Use lowercase hmac header (second parameter)
        ResponseEntity<String> response = controller.handleIpnNotification(
                1L, payload, null, validHmac);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void controller_HasRequiredDependencies() {
        assertNotNull(coinPaymentService);
        assertNotNull(paymentService);
        assertNotNull(webhookService);
        assertNotNull(webhookRepository);
    }
}
