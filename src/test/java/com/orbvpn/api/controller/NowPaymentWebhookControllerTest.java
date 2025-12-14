package com.orbvpn.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.entity.NowPayment;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.repository.ProcessedNowPaymentWebhookRepository;
import com.orbvpn.api.service.InvoiceService;
import com.orbvpn.api.service.payment.PaymentService;
import com.orbvpn.api.service.payment.nowpayment.NowPaymentService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NowPaymentWebhookControllerTest {

    @Mock
    private NowPaymentService nowPaymentService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private WebhookService webhookService;
    @Mock
    private ProcessedNowPaymentWebhookRepository processedWebhookRepository;

    private NowPaymentWebhookController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new NowPaymentWebhookController(
                nowPaymentService,
                paymentService,
                invoiceService,
                webhookService,
                objectMapper,
                processedWebhookRepository
        );
    }

    @Test
    void handleIpnCallback_ProcessesFinishedPayment() {
        String payload = """
                {
                    "payment_id": 12345678,
                    "payment_status": "finished",
                    "order_id": "ORB-TEST123",
                    "actually_paid": 0.00025,
                    "pay_currency": "btc",
                    "outcome_amount": 9.99,
                    "outcome_currency": "usdt"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        NowPayment mockNowPayment = mock(NowPayment.class);
        when(mockNowPayment.getPayment()).thenReturn(mockPayment);
        when(mockNowPayment.getId()).thenReturn(1L);

        when(nowPaymentService.findByOrderId("ORB-TEST123")).thenReturn(Optional.of(mockNowPayment));
        when(nowPaymentService.verifyIpnSignature(anyString(), anyString())).thenReturn(true);

        ResponseEntity<String> response = controller.handleIpnCallback(payload, "valid-signature");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("IPN processed successfully", response.getBody());

        verify(mockNowPayment).setPaymentStatus("finished");
        verify(nowPaymentService).save(mockNowPayment);
        verify(paymentService).fullFillPayment(mockPayment);
    }

    @Test
    void handleIpnCallback_ProcessesConfirmedPayment() {
        String payload = """
                {
                    "payment_id": 12345678,
                    "payment_status": "confirmed",
                    "order_id": "ORB-TEST123",
                    "actually_paid": 0.00025,
                    "pay_currency": "btc"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        NowPayment mockNowPayment = mock(NowPayment.class);
        when(mockNowPayment.getPayment()).thenReturn(mockPayment);
        when(mockNowPayment.getId()).thenReturn(1L);

        when(nowPaymentService.findByOrderId("ORB-TEST123")).thenReturn(Optional.of(mockNowPayment));

        ResponseEntity<String> response = controller.handleIpnCallback(payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(paymentService).fullFillPayment(mockPayment);
    }

    @Test
    void handleIpnCallback_ProcessesPartiallyPaid() {
        String payload = """
                {
                    "payment_id": 12345678,
                    "payment_status": "partially_paid",
                    "order_id": "ORB-TEST123",
                    "actually_paid": 0.00015,
                    "pay_currency": "btc"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        NowPayment mockNowPayment = mock(NowPayment.class);
        when(mockNowPayment.getPayment()).thenReturn(mockPayment);
        when(mockNowPayment.getId()).thenReturn(1L);
        when(mockNowPayment.getPayAmount()).thenReturn(new BigDecimal("0.00025"));

        when(nowPaymentService.findByOrderId("ORB-TEST123")).thenReturn(Optional.of(mockNowPayment));

        ResponseEntity<String> response = controller.handleIpnCallback(payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mockPayment).setStatus(PaymentStatus.PENDING);
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleIpnCallback_ProcessesFailedPayment() {
        String payload = """
                {
                    "payment_id": 12345678,
                    "payment_status": "failed",
                    "order_id": "ORB-TEST123"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        NowPayment mockNowPayment = mock(NowPayment.class);
        when(mockNowPayment.getPayment()).thenReturn(mockPayment);
        when(mockNowPayment.getId()).thenReturn(1L);

        when(nowPaymentService.findByOrderId("ORB-TEST123")).thenReturn(Optional.of(mockNowPayment));

        ResponseEntity<String> response = controller.handleIpnCallback(payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mockPayment).setStatus(PaymentStatus.FAILED);
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleIpnCallback_ProcessesExpiredPayment() {
        String payload = """
                {
                    "payment_id": 12345678,
                    "payment_status": "expired",
                    "order_id": "ORB-TEST123"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        NowPayment mockNowPayment = mock(NowPayment.class);
        when(mockNowPayment.getPayment()).thenReturn(mockPayment);
        when(mockNowPayment.getId()).thenReturn(1L);

        when(nowPaymentService.findByOrderId("ORB-TEST123")).thenReturn(Optional.of(mockNowPayment));

        ResponseEntity<String> response = controller.handleIpnCallback(payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mockPayment).setStatus(PaymentStatus.EXPIRED);
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleIpnCallback_ProcessesRefundedPayment() {
        String payload = """
                {
                    "payment_id": 12345678,
                    "payment_status": "refunded",
                    "order_id": "ORB-TEST123"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        NowPayment mockNowPayment = mock(NowPayment.class);
        when(mockNowPayment.getPayment()).thenReturn(mockPayment);
        when(mockNowPayment.getId()).thenReturn(1L);

        when(nowPaymentService.findByOrderId("ORB-TEST123")).thenReturn(Optional.of(mockNowPayment));

        ResponseEntity<String> response = controller.handleIpnCallback(payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mockPayment).setStatus(PaymentStatus.REFUNDED);
    }

    @Test
    void handleIpnCallback_HandlesPaymentNotFound() {
        String payload = """
                {
                    "payment_id": 12345678,
                    "payment_status": "finished",
                    "order_id": "NONEXISTENT"
                }
                """;

        when(nowPaymentService.findByOrderId("NONEXISTENT")).thenReturn(Optional.empty());
        when(nowPaymentService.findByPaymentId("12345678")).thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.handleIpnCallback(payload, null);

        // Returns 200 to stop retries
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Payment not found - acknowledged", response.getBody());
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleIpnCallback_FindsByPaymentIdIfOrderIdNotFound() {
        String payload = """
                {
                    "payment_id": 12345678,
                    "payment_status": "finished",
                    "order_id": "NONEXISTENT"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        NowPayment mockNowPayment = mock(NowPayment.class);
        when(mockNowPayment.getPayment()).thenReturn(mockPayment);
        when(mockNowPayment.getId()).thenReturn(1L);

        when(nowPaymentService.findByOrderId("NONEXISTENT")).thenReturn(Optional.empty());
        when(nowPaymentService.findByPaymentId("12345678")).thenReturn(Optional.of(mockNowPayment));

        ResponseEntity<String> response = controller.handleIpnCallback(payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("IPN processed successfully", response.getBody());
        verify(paymentService).fullFillPayment(mockPayment);
    }

    @Test
    void handleIpnCallback_RejectsInvalidSignature() {
        String payload = """
                {
                    "payment_id": 12345678,
                    "payment_status": "finished",
                    "order_id": "ORB-TEST123"
                }
                """;

        when(nowPaymentService.verifyIpnSignature(anyString(), anyString())).thenReturn(false);

        ResponseEntity<String> response = controller.handleIpnCallback(payload, "invalid-signature");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid signature", response.getBody());
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleIpnCallback_AcceptsNoSignature() {
        String payload = """
                {
                    "payment_id": 12345678,
                    "payment_status": "waiting",
                    "order_id": "ORB-TEST123"
                }
                """;

        NowPayment mockNowPayment = mock(NowPayment.class);
        when(mockNowPayment.getId()).thenReturn(1L);

        when(nowPaymentService.findByOrderId("ORB-TEST123")).thenReturn(Optional.of(mockNowPayment));

        ResponseEntity<String> response = controller.handleIpnCallback(payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void handleIpnCallback_HandlesInvalidPayload() {
        String payload = "invalid json";

        ResponseEntity<String> response = controller.handleIpnCallback(payload, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid payload format", response.getBody());
    }

    @Test
    void handleIpnCallback_ProcessesConfirmingStatus() {
        String payload = """
                {
                    "payment_id": 12345678,
                    "payment_status": "confirming",
                    "order_id": "ORB-TEST123"
                }
                """;

        NowPayment mockNowPayment = mock(NowPayment.class);
        when(mockNowPayment.getId()).thenReturn(1L);

        when(nowPaymentService.findByOrderId("ORB-TEST123")).thenReturn(Optional.of(mockNowPayment));

        ResponseEntity<String> response = controller.handleIpnCallback(payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mockNowPayment).setPaymentStatus("confirming");
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleIpnCallback_HandlesUnknownStatus() {
        String payload = """
                {
                    "payment_id": 12345678,
                    "payment_status": "unknown_status",
                    "order_id": "ORB-TEST123"
                }
                """;

        NowPayment mockNowPayment = mock(NowPayment.class);
        when(mockNowPayment.getId()).thenReturn(1L);

        when(nowPaymentService.findByOrderId("ORB-TEST123")).thenReturn(Optional.of(mockNowPayment));

        ResponseEntity<String> response = controller.handleIpnCallback(payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mockNowPayment).setPaymentStatus("unknown_status");
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleIpnCallback_UpdatesOutcomeFields() {
        String payload = """
                {
                    "payment_id": 12345678,
                    "payment_status": "finished",
                    "order_id": "ORB-TEST123",
                    "actually_paid": 0.00025,
                    "outcome_amount": 9.99,
                    "outcome_currency": "usdt"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        NowPayment mockNowPayment = mock(NowPayment.class);
        when(mockNowPayment.getPayment()).thenReturn(mockPayment);
        when(mockNowPayment.getId()).thenReturn(1L);

        when(nowPaymentService.findByOrderId("ORB-TEST123")).thenReturn(Optional.of(mockNowPayment));

        ResponseEntity<String> response = controller.handleIpnCallback(payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mockNowPayment).setOutcomeAmount(new BigDecimal("9.99"));
        verify(mockNowPayment).setOutcomeCurrency("usdt");
    }
}
