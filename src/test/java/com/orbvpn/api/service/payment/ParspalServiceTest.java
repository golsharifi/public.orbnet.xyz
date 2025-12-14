package com.orbvpn.api.service.payment;

import com.orbvpn.api.domain.dto.ParspalApprovePaymentResponse;
import com.orbvpn.api.domain.dto.ParspalCreatePaymentResponse;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.exception.PaymentException;
import com.orbvpn.api.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParspalServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RestTemplate restTemplate;

    private ParspalService parspalService;

    @BeforeEach
    void setUp() {
        parspalService = new ParspalService(paymentRepository);
        // Inject mocked RestTemplate and configuration values
        ReflectionTestUtils.setField(parspalService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(parspalService, "apiUrl", "https://api.parspal.com/v1");
        ReflectionTestUtils.setField(parspalService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(parspalService, "returnUrl", "https://orbvpn.com/callback");
    }

    // ==================== createPayment Tests ====================

    @Test
    void createPayment_SuccessfulCreation() {
        Payment payment = createMockPayment();
        ParspalCreatePaymentResponse apiResponse = new ParspalCreatePaymentResponse();
        apiResponse.setPayment_id("PARSPAL-123");
        apiResponse.setLink("https://parspal.com/pay/123");
        apiResponse.setStatus("100");

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(ParspalCreatePaymentResponse.class)))
                .thenReturn(ResponseEntity.ok(apiResponse));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        ParspalCreatePaymentResponse result = parspalService.createPayment(payment);

        assertNotNull(result);
        assertEquals("PARSPAL-123", result.getPayment_id());
        assertEquals("https://parspal.com/pay/123", result.getLink());
        verify(paymentRepository, times(1)).save(payment);
    }

    @Test
    void createPayment_ThrowsExceptionForNullPayment() {
        assertThrows(PaymentException.class, () -> parspalService.createPayment(null));
    }

    @Test
    void createPayment_ThrowsExceptionForNullAmount() {
        Payment payment = new Payment();
        payment.setId(1);
        payment.setPrice(null);

        assertThrows(PaymentException.class, () -> parspalService.createPayment(payment));
    }

    @Test
    void createPayment_ThrowsExceptionForZeroAmount() {
        Payment payment = new Payment();
        payment.setId(1);
        payment.setPrice(BigDecimal.ZERO);

        assertThrows(PaymentException.class, () -> parspalService.createPayment(payment));
    }

    @Test
    void createPayment_ThrowsExceptionForNegativeAmount() {
        Payment payment = new Payment();
        payment.setId(1);
        payment.setPrice(new BigDecimal("-10.00"));

        assertThrows(PaymentException.class, () -> parspalService.createPayment(payment));
    }

    @Test
    void createPayment_HandlesNullResponseBody() {
        Payment payment = createMockPayment();

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(ParspalCreatePaymentResponse.class)))
                .thenReturn(ResponseEntity.ok(null));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        assertThrows(PaymentException.class, () -> parspalService.createPayment(payment));
        verify(payment).setStatus(PaymentStatus.FAILED);
    }

    @Test
    void createPayment_HandlesApiErrorResponse() {
        Payment payment = createMockPayment();
        ParspalCreatePaymentResponse apiResponse = new ParspalCreatePaymentResponse();
        apiResponse.setError_type("VALIDATION_ERROR");
        apiResponse.setError_code("400");
        apiResponse.setMessage("Invalid amount");

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(ParspalCreatePaymentResponse.class)))
                .thenReturn(ResponseEntity.ok(apiResponse));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> parspalService.createPayment(payment));
        assertTrue(exception.getMessage().contains("VALIDATION_ERROR"));
        verify(payment).setStatus(PaymentStatus.FAILED);
    }

    @Test
    void createPayment_HandlesNullPaymentId() {
        Payment payment = createMockPayment();
        ParspalCreatePaymentResponse apiResponse = new ParspalCreatePaymentResponse();
        apiResponse.setPayment_id(null);
        apiResponse.setStatus("100");

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(ParspalCreatePaymentResponse.class)))
                .thenReturn(ResponseEntity.ok(apiResponse));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        assertThrows(PaymentException.class, () -> parspalService.createPayment(payment));
        verify(payment).setStatus(PaymentStatus.FAILED);
    }

    @Test
    void createPayment_HandlesEmptyPaymentId() {
        Payment payment = createMockPayment();
        ParspalCreatePaymentResponse apiResponse = new ParspalCreatePaymentResponse();
        apiResponse.setPayment_id("");
        apiResponse.setStatus("100");

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(ParspalCreatePaymentResponse.class)))
                .thenReturn(ResponseEntity.ok(apiResponse));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        assertThrows(PaymentException.class, () -> parspalService.createPayment(payment));
    }

    @Test
    void createPayment_HandlesRestClientException() {
        Payment payment = createMockPayment();

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(ParspalCreatePaymentResponse.class)))
                .thenThrow(new RestClientException("Connection timeout"));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> parspalService.createPayment(payment));
        assertTrue(exception.getMessage().contains("Failed to communicate"));
        verify(payment).setStatus(PaymentStatus.FAILED);
    }

    // ==================== approvePayment Tests ====================

    @Test
    void approvePayment_SuccessfulApproval() {
        Payment payment = createMockPayment();
        when(payment.getStatus()).thenReturn(PaymentStatus.PENDING);

        ParspalApprovePaymentResponse apiResponse = new ParspalApprovePaymentResponse();
        apiResponse.setStatus("100");

        when(paymentRepository.findByGatewayAndPaymentId(GatewayName.PARSPAL, "PARSPAL-123"))
                .thenReturn(Optional.of(payment));
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(ParspalApprovePaymentResponse.class)))
                .thenReturn(ResponseEntity.ok(apiResponse));

        boolean result = parspalService.approvePayment("PARSPAL-123", "RECEIPT-456");

        assertTrue(result);
    }

    @Test
    void approvePayment_ReturnsFalseForNullPaymentId() {
        boolean result = parspalService.approvePayment(null, "RECEIPT-456");
        assertFalse(result);
    }

    @Test
    void approvePayment_ReturnsFalseForEmptyPaymentId() {
        boolean result = parspalService.approvePayment("", "RECEIPT-456");
        assertFalse(result);
    }

    @Test
    void approvePayment_ReturnsFalseForNullReceipt() {
        boolean result = parspalService.approvePayment("PARSPAL-123", null);
        assertFalse(result);
    }

    @Test
    void approvePayment_ReturnsFalseForEmptyReceipt() {
        boolean result = parspalService.approvePayment("PARSPAL-123", "");
        assertFalse(result);
    }

    @Test
    void approvePayment_ThrowsNotFoundForUnknownPayment() {
        when(paymentRepository.findByGatewayAndPaymentId(GatewayName.PARSPAL, "NONEXISTENT"))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> parspalService.approvePayment("NONEXISTENT", "RECEIPT-456"));
    }

    @Test
    void approvePayment_ReturnsTrueForAlreadySucceededPayment() {
        Payment payment = createMockPayment();
        when(payment.getStatus()).thenReturn(PaymentStatus.SUCCEEDED);

        when(paymentRepository.findByGatewayAndPaymentId(GatewayName.PARSPAL, "PARSPAL-123"))
                .thenReturn(Optional.of(payment));

        boolean result = parspalService.approvePayment("PARSPAL-123", "RECEIPT-456");

        assertTrue(result);
        // Should not make API call for already succeeded payment
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void approvePayment_ReturnsFalseForAlreadyFailedPayment() {
        Payment payment = createMockPayment();
        when(payment.getStatus()).thenReturn(PaymentStatus.FAILED);

        when(paymentRepository.findByGatewayAndPaymentId(GatewayName.PARSPAL, "PARSPAL-123"))
                .thenReturn(Optional.of(payment));

        boolean result = parspalService.approvePayment("PARSPAL-123", "RECEIPT-456");

        assertFalse(result);
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void approvePayment_ReturnsFalseForNonSuccessStatus() {
        Payment payment = createMockPayment();
        when(payment.getStatus()).thenReturn(PaymentStatus.PENDING);

        ParspalApprovePaymentResponse apiResponse = new ParspalApprovePaymentResponse();
        apiResponse.setStatus("200"); // Not 100 = not approved

        when(paymentRepository.findByGatewayAndPaymentId(GatewayName.PARSPAL, "PARSPAL-123"))
                .thenReturn(Optional.of(payment));
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(ParspalApprovePaymentResponse.class)))
                .thenReturn(ResponseEntity.ok(apiResponse));

        boolean result = parspalService.approvePayment("PARSPAL-123", "RECEIPT-456");

        assertFalse(result);
    }

    @Test
    void approvePayment_ReturnsFalseForNullResponse() {
        Payment payment = createMockPayment();
        when(payment.getStatus()).thenReturn(PaymentStatus.PENDING);

        when(paymentRepository.findByGatewayAndPaymentId(GatewayName.PARSPAL, "PARSPAL-123"))
                .thenReturn(Optional.of(payment));
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(ParspalApprovePaymentResponse.class)))
                .thenReturn(ResponseEntity.ok(null));

        boolean result = parspalService.approvePayment("PARSPAL-123", "RECEIPT-456");

        assertFalse(result);
    }

    @Test
    void approvePayment_ReturnsFalseForNullStatus() {
        Payment payment = createMockPayment();
        when(payment.getStatus()).thenReturn(PaymentStatus.PENDING);

        ParspalApprovePaymentResponse apiResponse = new ParspalApprovePaymentResponse();
        apiResponse.setStatus(null);

        when(paymentRepository.findByGatewayAndPaymentId(GatewayName.PARSPAL, "PARSPAL-123"))
                .thenReturn(Optional.of(payment));
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(ParspalApprovePaymentResponse.class)))
                .thenReturn(ResponseEntity.ok(apiResponse));

        boolean result = parspalService.approvePayment("PARSPAL-123", "RECEIPT-456");

        assertFalse(result);
    }

    @Test
    void approvePayment_ReturnsFalseOnRestClientException() {
        Payment payment = createMockPayment();
        when(payment.getStatus()).thenReturn(PaymentStatus.PENDING);

        when(paymentRepository.findByGatewayAndPaymentId(GatewayName.PARSPAL, "PARSPAL-123"))
                .thenReturn(Optional.of(payment));
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(ParspalApprovePaymentResponse.class)))
                .thenThrow(new RestClientException("Network error"));

        boolean result = parspalService.approvePayment("PARSPAL-123", "RECEIPT-456");

        assertFalse(result);
    }

    // ==================== findByPaymentId Tests ====================

    @Test
    void findByPaymentId_ReturnsPayment() {
        Payment payment = createMockPayment();
        when(paymentRepository.findByGatewayAndPaymentId(GatewayName.PARSPAL, "PARSPAL-123"))
                .thenReturn(Optional.of(payment));

        Optional<Payment> result = parspalService.findByPaymentId("PARSPAL-123");

        assertTrue(result.isPresent());
    }

    @Test
    void findByPaymentId_ReturnsEmptyForUnknownPayment() {
        when(paymentRepository.findByGatewayAndPaymentId(GatewayName.PARSPAL, "UNKNOWN"))
                .thenReturn(Optional.empty());

        Optional<Payment> result = parspalService.findByPaymentId("UNKNOWN");

        assertFalse(result.isPresent());
    }

    // ==================== paymentExists Tests ====================

    @Test
    void paymentExists_ReturnsTrueForExistingPayment() {
        Payment payment = createRealPayment();
        when(paymentRepository.findByGatewayAndPaymentId(GatewayName.PARSPAL, "PARSPAL-123"))
                .thenReturn(Optional.of(payment));

        assertTrue(parspalService.paymentExists("PARSPAL-123"));
    }

    @Test
    void paymentExists_ReturnsFalseForNonexistentPayment() {
        when(paymentRepository.findByGatewayAndPaymentId(GatewayName.PARSPAL, "UNKNOWN"))
                .thenReturn(Optional.empty());

        assertFalse(parspalService.paymentExists("UNKNOWN"));
    }

    // ==================== isConfigured Tests ====================

    @Test
    void isConfigured_ReturnsTrueWhenFullyConfigured() {
        assertTrue(parspalService.isConfigured());
    }

    @Test
    void isConfigured_ReturnsFalseWhenApiKeyIsUnknown() {
        ReflectionTestUtils.setField(parspalService, "apiKey", "unknown");
        assertFalse(parspalService.isConfigured());
    }

    @Test
    void isConfigured_ReturnsFalseWhenReturnUrlIsUnknown() {
        ReflectionTestUtils.setField(parspalService, "returnUrl", "unknown");
        assertFalse(parspalService.isConfigured());
    }

    @Test
    void isConfigured_ReturnsFalseWhenApiUrlIsEmpty() {
        ReflectionTestUtils.setField(parspalService, "apiUrl", "");
        assertFalse(parspalService.isConfigured());
    }

    @Test
    void isConfigured_ReturnsFalseWhenApiUrlIsNull() {
        ReflectionTestUtils.setField(parspalService, "apiUrl", null);
        assertFalse(parspalService.isConfigured());
    }

    // ==================== URL Normalization Tests ====================

    @Test
    void createPayment_NormalizesUrlWithTrailingSlash() {
        Payment payment = createMockPayment();
        ParspalCreatePaymentResponse apiResponse = new ParspalCreatePaymentResponse();
        apiResponse.setPayment_id("PARSPAL-123");
        apiResponse.setStatus("100");

        ReflectionTestUtils.setField(parspalService, "apiUrl", "https://api.parspal.com/v1/");

        when(restTemplate.postForEntity(eq("https://api.parspal.com/v1/payment/request"),
                any(HttpEntity.class), eq(ParspalCreatePaymentResponse.class)))
                .thenReturn(ResponseEntity.ok(apiResponse));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        ParspalCreatePaymentResponse result = parspalService.createPayment(payment);

        assertNotNull(result);
        verify(restTemplate).postForEntity(eq("https://api.parspal.com/v1/payment/request"),
                any(HttpEntity.class), eq(ParspalCreatePaymentResponse.class));
    }

    // ==================== Helper Methods ====================

    private Payment createMockPayment() {
        Payment payment = mock(Payment.class);
        when(payment.getId()).thenReturn(1);
        when(payment.getPrice()).thenReturn(new BigDecimal("99.99"));
        when(payment.getPaymentId()).thenReturn("ORD-123");
        return payment;
    }

    private Payment createRealPayment() {
        Payment payment = new Payment();
        payment.setId(1);
        payment.setPrice(new BigDecimal("99.99"));
        payment.setPaymentId("ORD-123");
        payment.setGateway(GatewayName.PARSPAL);
        return payment;
    }
}
