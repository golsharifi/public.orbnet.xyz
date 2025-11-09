package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.SmsRequest;
import com.orbvpn.api.service.notification.SmsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SmsMutationResolver {

    private final SmsService smsService;

    @Secured(ADMIN)
    @MutationMapping
    public Boolean sendSms(@Argument @Valid SmsRequest smsRequest) {
        log.info("Sending SMS to: {}", smsRequest.getPhoneNumber());
        try {
            smsService.sendRequest(smsRequest);
            return true;
        } catch (Exception e) {
            log.error("Error sending SMS - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}