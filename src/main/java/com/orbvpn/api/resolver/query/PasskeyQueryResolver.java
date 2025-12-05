package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.passkey.OAuthConnectionView;
import com.orbvpn.api.domain.dto.passkey.PasskeyAuthenticationOptions;
import com.orbvpn.api.domain.dto.passkey.PasskeyRegistrationOptions;
import com.orbvpn.api.domain.dto.passkey.PasskeyView;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.PasskeyService;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.config.security.Unsecured;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PasskeyQueryResolver {

    private final PasskeyService passkeyService;
    private final UserService userService;

    @Secured({USER, ADMIN})
    @QueryMapping
    public PasskeyRegistrationOptions passkeyRegistrationOptions(@Argument String passkeyName) {
        log.info("Generating passkey registration options");
        User user = getCurrentUser();
        return passkeyService.generateRegistrationOptions(user, passkeyName);
    }

    @Unsecured
    @QueryMapping
    public PasskeyAuthenticationOptions passkeyAuthenticationOptions(@Argument String email) {
        log.info("Generating passkey authentication options for email: {}", email);
        if (email != null && !email.isEmpty()) {
            return passkeyService.generateAuthenticationOptions(email);
        } else {
            return passkeyService.generateAuthenticationOptionsDiscoverable();
        }
    }

    @Secured({USER, ADMIN})
    @QueryMapping
    public List<PasskeyView> myPasskeys() {
        log.info("Getting passkeys for current user");
        User user = getCurrentUser();
        return passkeyService.getUserPasskeys(user);
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<PasskeyView> userPasskeys(@Argument Integer userId) {
        log.info("Admin getting passkeys for user ID: {}", userId);
        return passkeyService.getPasskeysByUserId(userId);
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<OAuthConnectionView> userOAuthConnections(@Argument Integer userId) {
        log.info("Admin getting OAuth connections for user ID: {}", userId);
        return passkeyService.getOAuthConnectionsByUserId(userId);
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.getUserByUsername(username);
    }
}
