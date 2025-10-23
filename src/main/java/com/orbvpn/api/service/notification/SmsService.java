package com.orbvpn.api.service.notification;

import com.orbvpn.api.domain.dto.SmsRequest;
import com.orbvpn.api.exception.NotificationDeliveryException;
import com.orbvpn.api.service.notification.sms.SmsProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SmsService {
    private final List<SmsProvider> smsProviders;

    public void sendSms(String phoneNumber, String message) {
        try {
            log.debug("Sending SMS to: {}", phoneNumber);
            SmsProvider provider = getApplicableProvider(phoneNumber);
            provider.sendSms(phoneNumber, message);
            log.debug("SMS sent successfully to: {}", phoneNumber);
        } catch (Exception e) {
            log.error("Failed to send SMS to: {}", phoneNumber, e);
            throw new NotificationDeliveryException(
                    "SMS sending failed",
                    "SMS",
                    phoneNumber,
                    "UNKNOWN",
                    e);
        }
    }

    public void sendMessage(String phoneNumber, String message) {
        sendSms(phoneNumber, message);
    }

    public void sendRequest(SmsRequest smsRequest) {
        if (smsRequest == null) {
            throw new IllegalArgumentException("SMS request cannot be null");
        }
        log.debug("Processing SMS request for phone number: {}", smsRequest.getPhoneNumber());
        sendSms(smsRequest.getPhoneNumber(), smsRequest.getMessage());
    }

    private SmsProvider getApplicableProvider(String phoneNumber) {
        return smsProviders.stream()
                .filter(provider -> provider.isApplicable(phoneNumber))
                .findFirst()
                .orElseThrow(() -> new NotificationDeliveryException(
                        "No SMS provider available for phone number: " + phoneNumber,
                        "SMS",
                        phoneNumber,
                        "NO_PROVIDER",
                        new IllegalStateException("No applicable SMS provider found")));
    }
}