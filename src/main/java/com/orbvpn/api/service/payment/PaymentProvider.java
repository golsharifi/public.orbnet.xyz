package com.orbvpn.api.service.payment;

import com.orbvpn.api.domain.dto.PaymentRequest;
import com.orbvpn.api.domain.dto.PaymentResponse;
import com.orbvpn.api.exception.PaymentException;

public interface PaymentProvider {
    String getName();

    boolean supportsSubscriptions();

    boolean supportsDynamicPricing();

    PaymentResponse initiatePayment(PaymentRequest request) throws PaymentException;

    default void handleWebhook(String payload) throws PaymentException {
        throw new PaymentException("Webhook handling not implemented for " + getName());
    }
}