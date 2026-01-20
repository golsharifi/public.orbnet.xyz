package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.SignupResponse;
import com.orbvpn.api.domain.entity.UnverifiedUser;
import com.orbvpn.api.service.UserRegistrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import com.orbvpn.api.config.security.Unsecured;
import com.orbvpn.api.service.PasswordService;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SignupMutationResolver {

    private final UserRegistrationService userRegistrationService;
    private final PasswordService passwordService;

    @Unsecured
    @MutationMapping
    public SignupResponse signup(
            @Argument @Valid @Email String email,
            @Argument @Valid @NotBlank String password,
            @Argument String referral) {
        log.info("Processing signup request for email: {}", email);
        try {
            UnverifiedUser unverifiedUser = new UnverifiedUser();
            unverifiedUser.setEmail(email);
            passwordService.setPassword(unverifiedUser, password);
            return userRegistrationService.register(unverifiedUser);
        } catch (RuntimeException re) {
            log.error("Registration failed - Error: {}", re.getMessage(), re);
            return new SignupResponse(re.getMessage(), false);
        } catch (Exception e) {
            log.error("Unexpected error during registration - Error: {}", e.getMessage(), e);
            return new SignupResponse("Registration failed.", false);
        }
    }
}