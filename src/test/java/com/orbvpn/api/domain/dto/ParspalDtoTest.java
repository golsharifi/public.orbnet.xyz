package com.orbvpn.api.domain.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Parspal DTO classes
 */
class ParspalDtoTest {

    // ==================== ParspalCreatePaymentRequest Tests ====================

    @Test
    void createPaymentRequest_DefaultCurrencyIsUsd() {
        ParspalCreatePaymentRequest request = new ParspalCreatePaymentRequest();
        assertEquals("usd", request.getCurrency());
    }

    @Test
    void createPaymentRequest_CanSetAmount() {
        ParspalCreatePaymentRequest request = new ParspalCreatePaymentRequest();
        request.setAmount(99.99);
        assertEquals(99.99, request.getAmount());
    }

    @Test
    void createPaymentRequest_CanSetReturnUrl() {
        ParspalCreatePaymentRequest request = new ParspalCreatePaymentRequest();
        request.setReturnUrl("https://example.com/callback");
        assertEquals("https://example.com/callback", request.getReturnUrl());
    }

    @Test
    void createPaymentRequest_CanSetOrderId() {
        ParspalCreatePaymentRequest request = new ParspalCreatePaymentRequest();
        request.setOrderId("ORD-12345");
        assertEquals("ORD-12345", request.getOrderId());
    }

    @Test
    void createPaymentRequest_CanOverrideCurrency() {
        ParspalCreatePaymentRequest request = new ParspalCreatePaymentRequest();
        request.setCurrency("eur");
        assertEquals("eur", request.getCurrency());
    }

    // ==================== ParspalCreatePaymentResponse Tests ====================

    @Test
    void createPaymentResponse_CanSetPaymentId() {
        ParspalCreatePaymentResponse response = new ParspalCreatePaymentResponse();
        response.setPayment_id("PAY-12345");
        assertEquals("PAY-12345", response.getPayment_id());
    }

    @Test
    void createPaymentResponse_CanSetLink() {
        ParspalCreatePaymentResponse response = new ParspalCreatePaymentResponse();
        response.setLink("https://parspal.com/pay/12345");
        assertEquals("https://parspal.com/pay/12345", response.getLink());
    }

    @Test
    void createPaymentResponse_CanSetStatus() {
        ParspalCreatePaymentResponse response = new ParspalCreatePaymentResponse();
        response.setStatus("100");
        assertEquals("100", response.getStatus());
    }

    @Test
    void createPaymentResponse_CanSetMessage() {
        ParspalCreatePaymentResponse response = new ParspalCreatePaymentResponse();
        response.setMessage("Payment created successfully");
        assertEquals("Payment created successfully", response.getMessage());
    }

    @Test
    void createPaymentResponse_CanSetErrorType() {
        ParspalCreatePaymentResponse response = new ParspalCreatePaymentResponse();
        response.setError_type("VALIDATION_ERROR");
        assertEquals("VALIDATION_ERROR", response.getError_type());
    }

    @Test
    void createPaymentResponse_CanSetErrorCode() {
        ParspalCreatePaymentResponse response = new ParspalCreatePaymentResponse();
        response.setError_code("400");
        assertEquals("400", response.getError_code());
    }

    @Test
    void createPaymentResponse_DefaultValuesAreNull() {
        ParspalCreatePaymentResponse response = new ParspalCreatePaymentResponse();
        assertNull(response.getPayment_id());
        assertNull(response.getLink());
        assertNull(response.getStatus());
        assertNull(response.getMessage());
        assertNull(response.getError_type());
        assertNull(response.getError_code());
    }

    // ==================== ParspalApprovePaymentRequest Tests ====================

    @Test
    void approvePaymentRequest_DefaultCurrencyIsUsd() {
        ParspalApprovePaymentRequest request = new ParspalApprovePaymentRequest();
        assertEquals("usd", request.getCurrency());
    }

    @Test
    void approvePaymentRequest_CanSetReceipt() {
        ParspalApprovePaymentRequest request = new ParspalApprovePaymentRequest();
        request.setReceipt("REC-12345");
        assertEquals("REC-12345", request.getReceipt());
    }

    @Test
    void approvePaymentRequest_CanSetAmount() {
        ParspalApprovePaymentRequest request = new ParspalApprovePaymentRequest();
        request.setAmount(150.50);
        assertEquals(150.50, request.getAmount());
    }

    @Test
    void approvePaymentRequest_CanOverrideCurrency() {
        ParspalApprovePaymentRequest request = new ParspalApprovePaymentRequest();
        request.setCurrency("eur");
        assertEquals("eur", request.getCurrency());
    }

    // ==================== ParspalApprovePaymentResponse Tests ====================

    @Test
    void approvePaymentResponse_CanSetStatus() {
        ParspalApprovePaymentResponse response = new ParspalApprovePaymentResponse();
        response.setStatus("100");
        assertEquals("100", response.getStatus());
    }

    @Test
    void approvePaymentResponse_DefaultStatusIsNull() {
        ParspalApprovePaymentResponse response = new ParspalApprovePaymentResponse();
        assertNull(response.getStatus());
    }

    @Test
    void approvePaymentResponse_StatusCanBeNon100() {
        ParspalApprovePaymentResponse response = new ParspalApprovePaymentResponse();
        response.setStatus("200");
        assertEquals("200", response.getStatus());
    }

    // ==================== Integration Scenarios ====================

    @Test
    void successfulPaymentFlow_RequestAndResponseMatch() {
        // Create request
        ParspalCreatePaymentRequest createRequest = new ParspalCreatePaymentRequest();
        createRequest.setAmount(99.99);
        createRequest.setOrderId("ORD-123");
        createRequest.setReturnUrl("https://example.com/callback");

        // Simulate successful response
        ParspalCreatePaymentResponse createResponse = new ParspalCreatePaymentResponse();
        createResponse.setPayment_id("PAY-456");
        createResponse.setLink("https://parspal.com/pay/456");
        createResponse.setStatus("100");

        // Verify approval request uses same amount
        ParspalApprovePaymentRequest approveRequest = new ParspalApprovePaymentRequest();
        approveRequest.setAmount(createRequest.getAmount());
        approveRequest.setReceipt("REC-789");

        assertEquals(createRequest.getAmount(), approveRequest.getAmount());
    }

    @Test
    void errorResponse_HasErrorFields() {
        ParspalCreatePaymentResponse response = new ParspalCreatePaymentResponse();
        response.setError_type("INSUFFICIENT_FUNDS");
        response.setError_code("402");
        response.setMessage("Insufficient funds in account");

        assertNotNull(response.getError_type());
        assertNotNull(response.getError_code());
        assertNotNull(response.getMessage());
        assertNull(response.getPayment_id()); // No payment ID on error
    }
}
