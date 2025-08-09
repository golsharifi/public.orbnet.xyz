package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.ReferralCodeView;
import com.orbvpn.api.service.ReferralCodeService;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ReferralCodeQueryResolver {
    private final ReferralCodeService referralCodeService;

    @Secured(USER)
    @QueryMapping
    public ReferralCodeView getReferralCode() {
        log.info("Fetching referral code");
        try {
            ReferralCodeView referralCode = referralCodeService.getReferralCode();
            log.info("Successfully retrieved referral code");
            return referralCode;
        } catch (Exception e) {
            log.error("Error fetching referral code - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}