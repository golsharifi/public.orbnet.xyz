package com.orbvpn.api.service.notification.sms;

import com.orbvpn.api.config.sms.AfeConfig;
import com.orbvpn.api.exception.NotificationDeliveryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
@RequiredArgsConstructor
public class AfeSmsProvider implements SmsProvider {
    private final AfeConfig afeConfig;
    private final RestTemplate restTemplate;

    @Override
    public void sendSms(String phoneNumber, String message) {
        try {
            log.debug("Sending SMS via AFE.ir to: {}", phoneNumber);
            String formattedPhone = formatPhoneNumber(phoneNumber);
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);

            String url = String.format("http://www.afe.ir/Url/SendSMS.aspx?" +
                    "Username=%s&Password=%s&Number=%s&Mobile=%s&SMS=%s",
                    afeConfig.getUsername(),
                    afeConfig.getPassword(),
                    afeConfig.getNumber(),
                    formattedPhone,
                    encodedMessage);

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (!response.getBody().contains("send successfully")) {
                throw new NotificationDeliveryException(
                        "AFE.ir SMS sending failed: " + response.getBody(),
                        "SMS",
                        phoneNumber,
                        "AFE_ERROR",
                        new RuntimeException(response.getBody()));
            }

            log.debug("SMS sent successfully via AFE.ir to: {}", phoneNumber);
        } catch (Exception e) {
            log.error("AFE.ir error while sending SMS to {}: {}", phoneNumber, e.getMessage());
            throw new NotificationDeliveryException(
                    "SMS sending failed: " + e.getMessage(),
                    "SMS",
                    phoneNumber,
                    "AFE_ERROR",
                    e);
        }
    }

    @Override
    public boolean isApplicable(String phoneNumber) {
        return phoneNumber != null && phoneNumber.startsWith("+98");
    }

    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty");
        }

        String cleaned = phoneNumber.replaceAll("[^\\d+]", "");
        if (cleaned.startsWith("+98")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.length() != 10) {
            throw new IllegalArgumentException("Invalid Iranian phone number format");
        }

        return cleaned;
    }
}