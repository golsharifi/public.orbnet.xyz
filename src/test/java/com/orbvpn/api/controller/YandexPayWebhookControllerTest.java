package com.orbvpn.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.YandexPayment;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.repository.ProcessedYandexPayWebhookRepository;
import com.orbvpn.api.service.InvoiceService;
import com.orbvpn.api.service.payment.PaymentService;
import com.orbvpn.api.service.payment.yandexpay.YandexPayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class YandexPayWebhookControllerTest {

    @Mock
    private YandexPayService yandexPayService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private ProcessedYandexPayWebhookRepository processedWebhookRepository;

    private YandexPayWebhookController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new YandexPayWebhookController(
                yandexPayService,
                paymentService,
                invoiceService,
                objectMapper, processedWebhookRepository);
    }

    @Test
    void handleWebhook_ProcessesSuccessfulPayment() {
        String payload = """
                {
                    "event": "ORDER_PAID",
                    "orderId": "ORB-YP-TEST123",
                    "status": "CONFIRMED",
                    "amount": 999.99,
                    "currency": "RUB",
                    "paymentMethod": "CARD"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        when(mockPayment.getStatus()).thenReturn(PaymentStatus.PENDING);

        YandexPayment mockYandexPayment = mock(YandexPayment.class);
        when(mockYandexPayment.getPayment()).thenReturn(mockPayment);
        when(mockYandexPayment.getOrderId()).thenReturn("ORB-YP-TEST123");
        when(mockYandexPayment.isSuccessful()).thenReturn(false);

        when(yandexPayService.findByOrderId("ORB-YP-TEST123")).thenReturn(Optional.of(mockYandexPayment));

        ResponseEntity<String> response = controller.handleWebhook(payload, "application/json");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Webhook processed", response.getBody());

        verify(mockYandexPayment).setPaymentStatus(YandexPayment.STATUS_CONFIRMED);
        verify(paymentService).fullFillPayment(mockPayment);
        verify(yandexPayService).save(mockYandexPayment);
    }

    @Test
    void handleWebhook_SkipsAlreadySucceededPayment() {
        String payload = """
                {
                    "event": "ORDER_PAID",
                    "orderId": "ORB-YP-TEST123",
                    "status": "CONFIRMED"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        when(mockPayment.getStatus()).thenReturn(PaymentStatus.SUCCEEDED);

        YandexPayment mockYandexPayment = mock(YandexPayment.class);
        when(mockYandexPayment.getPayment()).thenReturn(mockPayment);
        when(mockYandexPayment.getOrderId()).thenReturn("ORB-YP-TEST123");
        when(mockYandexPayment.isSuccessful()).thenReturn(true);

        when(yandexPayService.findByOrderId("ORB-YP-TEST123")).thenReturn(Optional.of(mockYandexPayment));

        ResponseEntity<String> response = controller.handleWebhook(payload, "application/json");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleWebhook_ProcessesFailedPayment() {
        String payload = """
                {
                    "event": "ORDER_FAILED",
                    "orderId": "ORB-YP-TEST123",
                    "status": "FAILED",
                    "reason": "Card declined"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        YandexPayment mockYandexPayment = mock(YandexPayment.class);
        when(mockYandexPayment.getPayment()).thenReturn(mockPayment);
        when(mockYandexPayment.getOrderId()).thenReturn("ORB-YP-TEST123");
        when(mockYandexPayment.isSuccessful()).thenReturn(false);

        when(yandexPayService.findByOrderId("ORB-YP-TEST123")).thenReturn(Optional.of(mockYandexPayment));

        ResponseEntity<String> response = controller.handleWebhook(payload, "application/json");

        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(mockYandexPayment).markFailed("Card declined");
        verify(mockPayment).setStatus(PaymentStatus.FAILED);
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleWebhook_ProcessesCancelledPayment() {
        String payload = """
                {
                    "event": "ORDER_CANCELLED",
                    "orderId": "ORB-YP-TEST123",
                    "status": "CANCELLED"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        YandexPayment mockYandexPayment = mock(YandexPayment.class);
        when(mockYandexPayment.getPayment()).thenReturn(mockPayment);
        when(mockYandexPayment.getOrderId()).thenReturn("ORB-YP-TEST123");
        when(mockYandexPayment.isSuccessful()).thenReturn(false);

        when(yandexPayService.findByOrderId("ORB-YP-TEST123")).thenReturn(Optional.of(mockYandexPayment));

        ResponseEntity<String> response = controller.handleWebhook(payload, "application/json");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleWebhook_ProcessesRefundedPayment() {
        String payload = """
                {
                    "event": "ORDER_REFUNDED",
                    "orderId": "ORB-YP-TEST123",
                    "status": "REFUNDED"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        YandexPayment mockYandexPayment = mock(YandexPayment.class);
        when(mockYandexPayment.getPayment()).thenReturn(mockPayment);
        when(mockYandexPayment.getOrderId()).thenReturn("ORB-YP-TEST123");
        when(mockYandexPayment.isSuccessful()).thenReturn(false);

        when(yandexPayService.findByOrderId("ORB-YP-TEST123")).thenReturn(Optional.of(mockYandexPayment));

        ResponseEntity<String> response = controller.handleWebhook(payload, "application/json");

        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(mockYandexPayment).markRefunded();
        verify(mockPayment).setStatus(PaymentStatus.REFUNDED);
    }

    @Test
    void handleWebhook_HandlesPaymentNotFound() {
        String payload = """
                {
                    "event": "ORDER_PAID",
                    "orderId": "NONEXISTENT",
                    "status": "CONFIRMED"
                }
                """;

        when(yandexPayService.findByOrderId("NONEXISTENT")).thenReturn(Optional.empty());
        when(yandexPayService.findByYandexOrderId("NONEXISTENT")).thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.handleWebhook(payload, "application/json");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Payment not found - acknowledged", response.getBody());
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleWebhook_FindsByYandexOrderIdIfOrderIdNotFound() {
        String payload = """
                {
                    "event": "ORDER_PAID",
                    "orderId": "YP-123456",
                    "status": "CONFIRMED"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        when(mockPayment.getStatus()).thenReturn(PaymentStatus.PENDING);

        YandexPayment mockYandexPayment = mock(YandexPayment.class);
        when(mockYandexPayment.getPayment()).thenReturn(mockPayment);
        when(mockYandexPayment.getOrderId()).thenReturn("ORB-YP-TEST123");
        when(mockYandexPayment.isSuccessful()).thenReturn(false);

        when(yandexPayService.findByOrderId("YP-123456")).thenReturn(Optional.empty());
        when(yandexPayService.findByYandexOrderId("YP-123456")).thenReturn(Optional.of(mockYandexPayment));

        ResponseEntity<String> response = controller.handleWebhook(payload, "application/json");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Webhook processed", response.getBody());
        verify(paymentService).fullFillPayment(mockPayment);
    }

    @Test
    void handleWebhook_HandlesInvalidJsonPayload() {
        String payload = "{invalid json}";

        ResponseEntity<String> response = controller.handleWebhook(payload, "application/json");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void handleWebhook_RejectsInvalidJwtPayload() {
        // Non-JSON payload is treated as JWT and fails signature verification
        String payload = "invalid-jwt-token";

        when(yandexPayService.verifyWebhookSignature(anyString())).thenReturn(false);

        ResponseEntity<String> response = controller.handleWebhook(payload, "application/json");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Invalid signature", response.getBody());
    }

    @Test
    void handleWebhook_ProcessesPendingStatus() {
        String payload = """
                {
                    "event": "ORDER_CREATED",
                    "orderId": "ORB-YP-TEST123",
                    "status": "PENDING"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        YandexPayment mockYandexPayment = mock(YandexPayment.class);
        when(mockYandexPayment.getPayment()).thenReturn(mockPayment);
        when(mockYandexPayment.getOrderId()).thenReturn("ORB-YP-TEST123");
        when(mockYandexPayment.isSuccessful()).thenReturn(false);

        when(yandexPayService.findByOrderId("ORB-YP-TEST123")).thenReturn(Optional.of(mockYandexPayment));

        ResponseEntity<String> response = controller.handleWebhook(payload, "application/json");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mockYandexPayment).setPaymentStatus(YandexPayment.STATUS_PENDING);
        verify(paymentService, never()).fullFillPayment(any());
    }

    @Test
    void handleWebhook_ProcessesAuthorizedStatus() {
        String payload = """
                {
                    "event": "ORDER_CREATED",
                    "orderId": "ORB-YP-TEST123",
                    "status": "AUTHORIZED"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        YandexPayment mockYandexPayment = mock(YandexPayment.class);
        when(mockYandexPayment.getPayment()).thenReturn(mockPayment);
        when(mockYandexPayment.getOrderId()).thenReturn("ORB-YP-TEST123");
        when(mockYandexPayment.isSuccessful()).thenReturn(false);

        when(yandexPayService.findByOrderId("ORB-YP-TEST123")).thenReturn(Optional.of(mockYandexPayment));

        ResponseEntity<String> response = controller.handleWebhook(payload, "application/json");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mockYandexPayment).setPaymentStatus(YandexPayment.STATUS_AUTHORIZED);
        verify(mockPayment).setStatus(PaymentStatus.PROCESSING);
    }

    @Test
    void handleWebhook_UpdatesOperationId() {
        String payload = """
                {
                    "event": "ORDER_PAID",
                    "orderId": "ORB-YP-TEST123",
                    "status": "CONFIRMED",
                    "operationId": "op-123456"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        when(mockPayment.getStatus()).thenReturn(PaymentStatus.PENDING);

        YandexPayment mockYandexPayment = mock(YandexPayment.class);
        when(mockYandexPayment.getPayment()).thenReturn(mockPayment);
        when(mockYandexPayment.getOrderId()).thenReturn("ORB-YP-TEST123");
        when(mockYandexPayment.isSuccessful()).thenReturn(false);

        when(yandexPayService.findByOrderId("ORB-YP-TEST123")).thenReturn(Optional.of(mockYandexPayment));

        ResponseEntity<String> response = controller.handleWebhook(payload, "application/json");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mockYandexPayment).setOperationId("op-123456");
    }

    @Test
    void handleWebhook_UpdatesPaymentMethod() {
        String payload = """
                {
                    "event": "ORDER_PAID",
                    "orderId": "ORB-YP-TEST123",
                    "status": "CONFIRMED",
                    "paymentMethod": "CARD"
                }
                """;

        Payment mockPayment = mock(Payment.class);
        when(mockPayment.getStatus()).thenReturn(PaymentStatus.PENDING);

        YandexPayment mockYandexPayment = mock(YandexPayment.class);
        when(mockYandexPayment.getPayment()).thenReturn(mockPayment);
        when(mockYandexPayment.getOrderId()).thenReturn("ORB-YP-TEST123");
        when(mockYandexPayment.isSuccessful()).thenReturn(false);

        when(yandexPayService.findByOrderId("ORB-YP-TEST123")).thenReturn(Optional.of(mockYandexPayment));

        ResponseEntity<String> response = controller.handleWebhook(payload, "application/json");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mockYandexPayment).setPaymentMethod("CARD");
    }

    @Test
    void health_ReturnsOk() {
        ResponseEntity<String> response = controller.health();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("OK", response.getBody());
    }
}
