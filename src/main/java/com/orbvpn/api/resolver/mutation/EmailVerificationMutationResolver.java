package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.service.EmailVerificationService;
import com.orbvpn.api.config.security.Unsecured;
import com.orbvpn.api.domain.dto.AuthenticatedUser;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Slf4j
@Controller
@RequiredArgsConstructor
public class EmailVerificationMutationResolver {
    private final EmailVerificationService emailVerificationService;

    @Unsecured
    @MutationMapping
    public boolean resendVerificationEmail(
            @Argument @Valid @Email(message = "Invalid email format") String email) {
        log.info("Resending verification email to: {}", email);
        try {
            emailVerificationService.resendVerificationEmail(email);
            log.info("Successfully resent verification email to: {}", email);
            return true;
        } catch (RuntimeException re) {
            log.error("Runtime error resending verification email - Error: {}", re.getMessage(), re);
            throw re;
        } catch (Exception e) {
            log.error("Error resending verification email - Error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to resend verification email");
        }
    }

    @Unsecured
    @MutationMapping
    public AuthenticatedUser verifyEmailWithCode(
            @Argument @Valid @Email(message = "Invalid email format") String email,
            @Argument @Valid @NotBlank(message = "Verification code cannot be empty") String verificationCode) {
        log.info("Verifying email code for: {}", email);
        try {
            AuthenticatedUser user = emailVerificationService.verifyEmailWithCode(email, verificationCode);
            log.info("Successfully verified email code for: {}", email);
            return user;
        } catch (RuntimeException re) {
            log.error("Runtime error verifying email code - Error: {}", re.getMessage(), re);
            throw re;
        } catch (Exception e) {
            log.error("Error verifying email code - Error: {}", e.getMessage(), e);
            throw new RuntimeException("Verification failed");
        }
    }

    @Unsecured
    @MutationMapping
    public Boolean verifyResetPasswordCode(
            @Argument @Valid @Email(message = "Invalid email format") String email,
            @Argument @Valid @NotBlank(message = "Verification code cannot be empty") String verificationCode) {
        log.info("Verifying password reset code for: {}", email);
        try {
            Boolean result = emailVerificationService.verifyResetPasswordCode(email, verificationCode);
            log.info("Successfully verified password reset code for: {}", email);
            return result;
        } catch (RuntimeException re) {
            log.error("Runtime error verifying reset code - Error: {}", re.getMessage(), re);
            throw re;
        } catch (Exception e) {
            log.error("Error verifying reset code - Error: {}", e.getMessage(), e);
            throw new RuntimeException("Verification reset password failed");
        }
    }
}