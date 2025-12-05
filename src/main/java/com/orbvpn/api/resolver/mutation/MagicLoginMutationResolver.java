package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.config.security.Unsecured;
import com.orbvpn.api.domain.dto.AuthenticatedUser;
import com.orbvpn.api.service.MagicLoginService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Controller
@RequiredArgsConstructor
@Validated
public class MagicLoginMutationResolver {

    private final MagicLoginService magicLoginService;

    /**
     * Request a magic login code to be sent to the user's email
     * This mutation is unsecured as users need to request login without being authenticated
     */
    @Unsecured
    @MutationMapping
    public boolean requestMagicLogin(@Argument @Valid @Email String email) {
        log.info("Magic login code request for email: {}", email);
        try {
            return magicLoginService.requestMagicLogin(email);
        } catch (Exception e) {
            log.error("Failed to request magic login for email: {} - Error: {}", email, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Verify a magic login code and authenticate the user
     * This mutation is unsecured as users need to verify without being authenticated
     */
    @Unsecured
    @MutationMapping
    public AuthenticatedUser verifyMagicLogin(
            @Argument @Valid @Email String email,
            @Argument @Valid @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Code must be 6 digits") String code) {
        log.info("Magic login verification for email: {}", email);
        try {
            AuthenticatedUser result = magicLoginService.verifyMagicLogin(email, code);
            log.info("Magic login verification successful for email: {}", email);
            return result;
        } catch (Exception e) {
            log.error("Magic login verification failed for email: {} - Error: {}", email, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Request a magic link to be sent to the user's email
     * This mutation is unsecured as users need to request login without being authenticated
     * Unlike magic login codes (6-digit), magic links are URL-based tokens that users can click directly
     */
    @Unsecured
    @MutationMapping
    public boolean requestMagicLink(@Argument @Valid @Email String email) {
        log.info("Magic link request for email: {}", email);
        try {
            return magicLoginService.requestMagicLink(email);
        } catch (Exception e) {
            log.error("Failed to request magic link for email: {} - Error: {}", email, e.getMessage(), e);
            throw e;
        }
    }
}
