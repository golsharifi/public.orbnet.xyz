package com.orbvpn.api.service.notification.sms;

import com.orbvpn.api.config.sms.TwilioConfig;
import com.orbvpn.api.exception.NotificationDeliveryException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Twilio SMS provider for international phone numbers.
 * Note: Twilio is initialized once by TwilioInitializer configuration class.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TwilioSmsProvider implements SmsProvider {
    private final TwilioConfig twilioConfig;

    @Override
    public void sendSms(String phoneNumber, String message) {
        try {
            log.debug("Sending SMS via Twilio to: {}", phoneNumber);
            String formattedPhone = formatPhoneNumber(phoneNumber);

            Message.creator(
                    new PhoneNumber(formattedPhone),
                    new PhoneNumber(twilioConfig.getPhoneNumber()),
                    message)
                    .create();

            log.debug("SMS sent successfully via Twilio to: {}", phoneNumber);
        } catch (Exception e) {
            log.error("Twilio error while sending SMS to {}: {}", phoneNumber, e.getMessage());
            throw new NotificationDeliveryException(
                    "SMS sending failed: " + e.getMessage(),
                    "SMS",
                    phoneNumber,
                    "TWILIO_ERROR",
                    e);
        }
    }

    @Override
    public boolean isApplicable(String phoneNumber) {
        return phoneNumber != null && !phoneNumber.startsWith("+98");
    }

    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty");
        }

        String cleaned = phoneNumber.replaceAll("[^\\d+]", "");
        if (!cleaned.startsWith("+")) {
            cleaned = "+" + cleaned;
        }

        if (cleaned.length() < 8) {
            throw new IllegalArgumentException("Invalid phone number format");
        }

        return cleaned;
    }
}