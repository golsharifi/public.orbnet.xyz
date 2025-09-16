package com.orbvpn.api.config.sms;

import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "twilio")
@Validated
@Data
public class TwilioConfig {
    @NotBlank(message = "Twilio Account SID is required")
    private String accountSid;

    @NotBlank(message = "Twilio Auth Token is required")
    private String authToken;

    @NotBlank(message = "Twilio Phone Number is required")
    private String phoneNumber;
}
