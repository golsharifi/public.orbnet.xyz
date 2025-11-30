package com.orbvpn.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.ProcessedPayPalWebhook;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.repository.PaymentRepository;
import com.orbvpn.api.repository.ProcessedPayPalWebhookRepository;
import com.orbvpn.api.service.InvoiceService;
import com.orbvpn.api.service.payment.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayPalWebhookControllerTest {

    @Mock
    private ProcessedPayPalWebhookRepository webhookRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentService paymentService;
    @Mock
    private InvoiceService invoiceService;

    private PayPalWebhookController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new PayPalWebhookController(
                webhookRepository,
                paymentRepository,
                paymentService,
                invoiceService,
                objectMapper
        );
        ReflectionTestUtils.setField(controller, "webhookId", "");
        ReflectionTestUtils.setField(controller, "verifySignature", false);
    }

    @Test
    void handleWebhook_ReturnsOk_WhenAlreadyProcessed() {
        String payload = """
                {
                    "id": "WH-123456789",
                    "event_type": "PAYMENT.CAPTURE.COMPLETED",
                    "resource": {
                        "id": "5O190127TN364715T"
                    },
                    "resource_type": "capture"
                }
                """;

        when(webhookRepository.existsByEventId("WH-123456789")).thenReturn(true);

        ResponseEntity<String> response = controller.handleWebhook(
                payload, "trans-id", "2024-01-15T10:00:00Z", "sig", "cert-url", "SHA256");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Webhook already processed", response.getBody());
        verify(webhookRepository, never()).save(any());
    }

    @Test
    void handleWebhook_ProcessesCaptureCompleted() {
        String payload = """
                {
                    "id": "WH-123456789",
                    "event_type": "PAYMENT.CAPTURE.COMPLETED",
                    "resource": {
                        "id": "5O190127TN364715T",
                        "amount": {
                            "value": "9.99",
                            "currency_code": "USD"
                        },
                        "supplementary_data": {
                            "related_ids": {
                                "order_id": "ORDER123"
                            }
                        }
                    },
                    "resource_type": "capture"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        when(mockPayment.getId()).thenReturn(100);
        when(mockPayment.getStatus()).thenReturn(PaymentStatus.PENDING);
        when(mockPayment.getPrice()).thenReturn(new BigDecimal("9.99"));

        when(webhookRepository.existsByEventId("WH-123456789")).thenReturn(false);
        when(paymentRepository.findByGatewayAndPaymentId(GatewayName.PAYPAL, "ORDER123"))
                .thenReturn(Optional.of(mockPayment));

        ResponseEntity<String> response = controller.handleWebhook(
                payload, "trans-id", "2024-01-15T10:00:00Z", "sig", "cert-url", "SHA256");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Webhook processed", response.getBody());

        verify(mockPayment).setStatus(PaymentStatus.SUCCEEDED);
        verify(paymentRepository).save(mockPayment);
        verify(paymentService).fullFillPayment(mockPayment);
        verify(webhookRepository).save(any(ProcessedPayPalWebhook.class));
    }

    @Test
    void handleWebhook_SkipsAlreadySucceededPayment() {
        String payload = """
                {
                    "id": "WH-123456789",
                    "event_type": "PAYMENT.CAPTURE.COMPLETED",
                    "resource": {
                        "id": "5O190127TN364715T",
                        "supplementary_data": {
                            "related_ids": {
                                "order_id": "ORDER123"
                            }
                        }
                    },
                    "resource_type": "capture"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        when(mockPayment.getId()).thenReturn(100);
        when(mockPayment.getStatus()).thenReturn(PaymentStatus.SUCCEEDED);

        when(webhookRepository.existsByEventId("WH-123456789")).thenReturn(false);
        when(paymentRepository.findByGatewayAndPaymentId(GatewayName.PAYPAL, "ORDER123"))
                .thenReturn(Optional.of(mockPayment));

        ResponseEntity<String> response = controller.handleWebhook(
                payload, "trans-id", "2024-01-15T10:00:00Z", "sig", "cert-url", "SHA256");

        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Should not fulfill again
        verify(paymentService, never()).fullFillPayment(any());
        verify(webhookRepository).save(any(ProcessedPayPalWebhook.class));
    }

    @Test
    void handleWebhook_ProcessesCaptureDenied() {
        String payload = """
                {
                    "id": "WH-123456789",
                    "event_type": "PAYMENT.CAPTURE.DENIED",
                    "resource": {
                        "id": "5O190127TN364715T",
                        "supplementary_data": {
                            "related_ids": {
                                "order_id": "ORDER123"
                            }
                        }
                    },
                    "resource_type": "capture"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        when(mockPayment.getId()).thenReturn(100);

        when(webhookRepository.existsByEventId("WH-123456789")).thenReturn(false);
        when(paymentRepository.findByGatewayAndPaymentId(eq(GatewayName.PAYPAL), anyString()))
                .thenReturn(Optional.of(mockPayment));

        ResponseEntity<String> response = controller.handleWebhook(
                payload, "trans-id", "2024-01-15T10:00:00Z", "sig", "cert-url", "SHA256");

        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(mockPayment).setStatus(PaymentStatus.FAILED);
        verify(paymentRepository).save(mockPayment);
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleWebhook_HandlesPaymentNotFound() {
        String payload = """
                {
                    "id": "WH-123456789",
                    "event_type": "PAYMENT.CAPTURE.COMPLETED",
                    "resource": {
                        "id": "5O190127TN364715T",
                        "supplementary_data": {
                            "related_ids": {
                                "order_id": "NONEXISTENT"
                            }
                        }
                    },
                    "resource_type": "capture"
                }
                """;

        when(webhookRepository.existsByEventId("WH-123456789")).thenReturn(false);
        when(paymentRepository.findByGatewayAndPaymentId(eq(GatewayName.PAYPAL), anyString()))
                .thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.handleWebhook(
                payload, "trans-id", "2024-01-15T10:00:00Z", "sig", "cert-url", "SHA256");

        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Should still save webhook as processed (with failed status)
        verify(webhookRepository).save(any(ProcessedPayPalWebhook.class));
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleWebhook_SkipsUnknownEventType() {
        String payload = """
                {
                    "id": "WH-123456789",
                    "event_type": "UNKNOWN.EVENT.TYPE",
                    "resource": {
                        "id": "test-resource"
                    },
                    "resource_type": "unknown"
                }
                """;

        when(webhookRepository.existsByEventId("WH-123456789")).thenReturn(false);

        ResponseEntity<String> response = controller.handleWebhook(
                payload, "trans-id", "2024-01-15T10:00:00Z", "sig", "cert-url", "SHA256");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleWebhook_HandlesInvalidPayload() {
        String payload = "invalid json";

        ResponseEntity<String> response = controller.handleWebhook(
                payload, "trans-id", "2024-01-15T10:00:00Z", "sig", "cert-url", "SHA256");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void handleWebhook_ProcessesOrderApproved() {
        String payload = """
                {
                    "id": "WH-ORDER-APPROVED",
                    "event_type": "CHECKOUT.ORDER.APPROVED",
                    "resource": {
                        "id": "ORDER123"
                    },
                    "resource_type": "checkout-order"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        when(mockPayment.getId()).thenReturn(100);
        when(mockPayment.getStatus()).thenReturn(PaymentStatus.PENDING);

        when(webhookRepository.existsByEventId("WH-ORDER-APPROVED")).thenReturn(false);
        when(paymentRepository.findByGatewayAndPaymentId(GatewayName.PAYPAL, "ORDER123"))
                .thenReturn(Optional.of(mockPayment));

        ResponseEntity<String> response = controller.handleWebhook(
                payload, "trans-id", "2024-01-15T10:00:00Z", "sig", "cert-url", "SHA256");

        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(mockPayment).setStatus(PaymentStatus.PROCESSING);
        verify(paymentRepository).save(mockPayment);
    }

    @Test
    void controller_HasRequiredDependencies() {
        assertNotNull(webhookRepository);
        assertNotNull(paymentRepository);
        assertNotNull(paymentService);
    }
}
