// src/main/java/com/orbvpn/api/listener/WebhookEventListener.java
package com.orbvpn.api.listener;

import com.orbvpn.api.domain.enums.WebhookEventType;
import com.orbvpn.api.event.PaymentEvent;
import com.orbvpn.api.event.UserEvent;
import com.orbvpn.api.event.DeviceEvent;
import com.orbvpn.api.event.ResellerEvent;
import com.orbvpn.api.event.SystemEvent;
import com.orbvpn.api.event.UsageEvent;
import com.orbvpn.api.service.webhook.WebhookEventProvider;
import com.orbvpn.api.service.webhook.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.util.Map;
import java.util.HashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebhookEventListener {
    private final WebhookService webhookService;
    private final WebhookEventProvider webhookEventProvider;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentEvent(PaymentEvent event) {
        try {
            WebhookEventType eventType = switch (event.getEventType()) {
                case "PAYMENT_CREATED" -> WebhookEventType.PAYMENT_CREATED;
                case "PAYMENT_PENDING" -> WebhookEventType.PAYMENT_PENDING;
                case "PAYMENT_SUCCEEDED" -> WebhookEventType.PAYMENT_SUCCEEDED;
                case "PAYMENT_FAILED" -> WebhookEventType.PAYMENT_FAILED;
                case "PAYMENT_REFUNDED" -> WebhookEventType.PAYMENT_REFUNDED;
                case "PAYMENT_DISPUTED" -> WebhookEventType.PAYMENT_DISPUTED;
                case "PAYMENT_CANCELLED" -> WebhookEventType.PAYMENT_CANCELLED;
                case "APPLE_PAYMENT_RECEIVED" -> WebhookEventType.APPLE_PAYMENT_RECEIVED;
                case "GOOGLE_PAYMENT_RECEIVED" -> WebhookEventType.GOOGLE_PAYMENT_RECEIVED;
                default -> null;
            };

            if (eventType != null) {
                webhookService.processWebhook(
                        eventType.getEventName(),
                        webhookEventProvider.createPaymentPayload(event.getPayment()));
            }
        } catch (Exception e) {
            log.error("Error processing webhook for payment event: {}", event.getEventType(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserEvent(UserEvent event) {
        try {
            WebhookEventType eventType = switch (event.getEventType()) {
                case "USER_CREATED" -> WebhookEventType.USER_CREATED;
                case "USER_UPDATED" -> WebhookEventType.USER_UPDATED;
                case "USER_DELETED" -> WebhookEventType.USER_DELETED;
                case "USER_ACTIVATED" -> WebhookEventType.USER_ACTIVATED;
                case "USER_DEACTIVATED" -> WebhookEventType.USER_DEACTIVATED;
                case "USER_SOFT_DELETED" -> WebhookEventType.USER_SOFT_DELETED;
                case "USER_ACCOUNT_DELETED" -> WebhookEventType.USER_ACCOUNT_DELETED;
                case "USER_EMAIL_CHANGED" -> WebhookEventType.USER_EMAIL_CHANGED;
                case "USER_PROFILE_UPDATED" -> WebhookEventType.USER_PROFILE_UPDATED;
                case "PASSWORD_CHANGED" -> WebhookEventType.PASSWORD_CHANGED;
                case "PASSWORD_RESET_REQUESTED" -> WebhookEventType.PASSWORD_RESET_REQUESTED;
                case "PASSWORD_RESET_COMPLETED" -> WebhookEventType.PASSWORD_RESET_COMPLETED;
                default -> null;
            };

            if (eventType != null) {
                webhookService.processWebhook(
                        eventType.getEventName(),
                        webhookEventProvider.createUserPayload(event.getUser(), event.getEventType()));
            }
        } catch (Exception e) {
            log.error("Error processing webhook for user event: {}", event.getEventType(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDeviceEvent(DeviceEvent event) {
        try {
            WebhookEventType eventType = switch (event.getEventType()) {
                case "DEVICE_CONNECTED" -> WebhookEventType.DEVICE_CONNECTED;
                case "DEVICE_DISCONNECTED" -> WebhookEventType.DEVICE_DISCONNECTED;
                case "DEVICE_BLOCKED" -> WebhookEventType.DEVICE_BLOCKED;
                case "DEVICE_ADDED" -> WebhookEventType.DEVICE_ADDED;
                case "DEVICE_REMOVED" -> WebhookEventType.DEVICE_REMOVED;
                case "DEVICE_LIMIT_REACHED" -> WebhookEventType.DEVICE_LIMIT_REACHED;
                default -> null;
            };

            if (eventType != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("deviceId", event.getDeviceId());
                payload.put("userId", event.getUserId());
                payload.put("deviceType", event.getDeviceType());
                payload.put("timestamp", event.getTimestamp());

                webhookService.processWebhook(eventType.getEventName(), payload);
            }
        } catch (Exception e) {
            log.error("Error processing webhook for device event: {}", event.getEventType(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUsageEvent(UsageEvent event) {
        try {
            WebhookEventType eventType = switch (event.getEventType()) {
                case "BANDWIDTH_EXCEEDED" -> WebhookEventType.BANDWIDTH_EXCEEDED;
                case "BANDWIDTH_WARNING" -> WebhookEventType.BANDWIDTH_WARNING;
                case "USAGE_REPORT" -> WebhookEventType.USAGE_REPORT;
                case "DAILY_LIMIT_REACHED" -> WebhookEventType.DAILY_LIMIT_REACHED;
                default -> null;
            };

            if (eventType != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("userId", event.getUserId());
                payload.put("usage", event.getUsage());
                payload.put("limit", event.getLimit());
                payload.put("timestamp", event.getTimestamp());

                webhookService.processWebhook(eventType.getEventName(), payload);
            }
        } catch (Exception e) {
            log.error("Error processing webhook for usage event: {}", event.getEventType(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleResellerEvent(ResellerEvent event) {
        try {
            WebhookEventType eventType = switch (event.getEventType()) {
                case "RESELLER_CREATED" -> WebhookEventType.RESELLER_CREATED;
                case "RESELLER_UPDATED" -> WebhookEventType.RESELLER_UPDATED;
                case "RESELLER_DELETED" -> WebhookEventType.RESELLER_DELETED;
                case "RESELLER_CREDIT_ADDED" -> WebhookEventType.RESELLER_CREDIT_ADDED;
                case "RESELLER_CREDIT_DEDUCTED" -> WebhookEventType.RESELLER_CREDIT_DEDUCTED;
                default -> null;
            };

            if (eventType != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("resellerId", event.getResellerId());
                payload.put("amount", event.getAmount());
                payload.put("action", event.getAction());
                payload.put("timestamp", event.getTimestamp());

                webhookService.processWebhook(eventType.getEventName(), payload);
            }
        } catch (Exception e) {
            log.error("Error processing webhook for reseller event: {}", event.getEventType(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSystemEvent(SystemEvent event) {
        try {
            WebhookEventType eventType = switch (event.getEventType()) {
                case "SYSTEM_ERROR" -> WebhookEventType.SYSTEM_ERROR;
                case "SYSTEM_WARNING" -> WebhookEventType.SYSTEM_WARNING;
                case "SYSTEM_MAINTENANCE" -> WebhookEventType.SYSTEM_MAINTENANCE;
                case "SYSTEM_UPDATE" -> WebhookEventType.SYSTEM_UPDATE;
                case "API_ERROR" -> WebhookEventType.API_ERROR;
                case "DATABASE_ERROR" -> WebhookEventType.DATABASE_ERROR;
                default -> null;
            };

            if (eventType != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("message", event.getMessage());
                payload.put("severity", event.getSeverity());
                payload.put("source", event.getSource());
                payload.put("timestamp", event.getTimestamp());

                webhookService.processWebhook(eventType.getEventName(), payload);
            }
        } catch (Exception e) {
            log.error("Error processing webhook for system event: {}", event.getEventType(), e);
        }
    }
}
