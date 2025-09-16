package com.orbvpn.api.service.webhook;

import com.orbvpn.api.domain.entity.*;
import java.util.Map;

public interface WebhookEventProvider {
    Map<String, Object> createSubscriptionPayload(UserSubscription subscription);
    Map<String, Object> createUserPayload(User user, String action);
    Map<String, Object> createPaymentPayload(Payment payment);
    Map<String, Object> createInvoicePayload(Invoice invoice);
    Map<String, Object> createPayloadWithExtra(User user, String action, Map<String, Object> extraData);
}
