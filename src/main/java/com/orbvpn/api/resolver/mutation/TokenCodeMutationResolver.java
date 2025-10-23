package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.TokenCodeDto;
import com.orbvpn.api.domain.dto.TokenCodeResponse;
import com.orbvpn.api.domain.dto.TokenCodeView;
import com.orbvpn.api.service.TokenCodeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
public class TokenCodeMutationResolver {

    private final TokenCodeService tokenCodeService;

    @Secured(ADMIN)
    @MutationMapping
    public TokenCodeResponse generateTokenCodeForUser(@Argument @Valid TokenCodeDto tokenCodeDto) {
        log.info("Generating token code");
        try {
            return tokenCodeService.generateTokenCodeForUser(tokenCodeDto);
        } catch (Exception e) {
            log.error("Error generating token code - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @MutationMapping
    public TokenCodeView useTokenCode(@Argument @Valid @NotBlank String code) {
        log.info("Using token code: {}", code);
        try {
            return tokenCodeService.useTokenCode(code);
        } catch (Exception e) {
            log.error("Error using token code - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @MutationMapping
    public TokenCodeView checkTokenCode(@Argument @Valid @NotBlank String code) {
        log.info("Checking token code: {}", code);
        try {
            return tokenCodeService.checkTokenCode(code);
        } catch (Exception e) {
            log.error("Error checking token code - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}