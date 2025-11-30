package com.orbvpn.api.config.sms;

import com.twilio.Twilio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class TwilioInitializer {

    public TwilioInitializer(TwilioConfig twilioConfig) {
        Twilio.init(
                twilioConfig.getAccountSid(),
                twilioConfig.getAuthToken());
        log.info("twilio Initialized with: " + twilioConfig.getAccountSid());
    }
}
