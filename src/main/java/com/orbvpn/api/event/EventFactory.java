package com.orbvpn.api.event;

import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.User;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class EventFactory {

    public PaymentEvent createPaymentEvent(Object source, Payment payment, String eventType) {
        return new PaymentEvent(source, payment, eventType);
    }

    public UserEvent createUserEvent(Object source, User user, String eventType) {
        return new UserEvent(source, user, eventType);
    }

    public UserEvent createUserEvent(Object source, User user, String eventType, String detail) {
        return new UserEvent(source, user, eventType, detail);
    }

    public DeviceEvent createDeviceEvent(Object source, String deviceId, Long userId,
            String deviceType, String eventType, String ipAddress, String location) {
        return new DeviceEvent(source, deviceId, userId, deviceType, eventType, ipAddress, location);
    }

    public ResellerEvent createResellerEvent(Object source, Long resellerId, String eventType,
            String action, BigDecimal amount, String currency, String notes) {
        return new ResellerEvent(source, resellerId, eventType, action, amount, currency, notes);
    }

    public SystemEvent createSystemEvent(Object source, String eventType, String message,
            String severity, String componentName, String stackTrace) {
        return new SystemEvent(source, eventType, message, severity, componentName, stackTrace);
    }

    public UsageEvent createUsageEvent(Object source, Long userId, String eventType,
            Long usage, Long limit, String metricType, String period) {
        return new UsageEvent(source, userId, eventType, usage, limit, metricType, period);
    }
}
