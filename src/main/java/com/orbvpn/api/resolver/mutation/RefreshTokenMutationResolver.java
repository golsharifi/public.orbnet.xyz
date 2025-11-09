package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.config.security.JwtTokenUtil;
import com.orbvpn.api.config.security.Unsecured;
import com.orbvpn.api.domain.dto.AuthenticatedUser;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.service.UserService;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RefreshTokenMutationResolver {

    private final JwtTokenUtil jwtTokenUtil;
    private final UserRepository userRepository;
    private final UserService userService;

    @Unsecured
    @MutationMapping
    public AuthenticatedUser refreshToken(
            @Argument @NotBlank(message = "Refresh token is required") String refreshToken) {
        
        log.info("Refresh token mutation called");

        try {
            // 1. Validate refresh token
            if (!jwtTokenUtil.validate(refreshToken)) {
                log.error("Invalid refresh token");
                throw new IllegalArgumentException("Invalid refresh token");
            }

            // 2. Check if it's actually a refresh token (not access token)
            if (!jwtTokenUtil.isRefreshToken(refreshToken)) {
                log.error("Provided token is not a refresh token");
                throw new IllegalArgumentException("Invalid token type. Expected refresh token.");
            }

            // 3. Extract username from refresh token
            String username = jwtTokenUtil.getUsername(refreshToken);
            log.info("Refresh token request for user: {}", username);

            // 4. Load user from database
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> {
                        log.error("User not found: {}", username);
                        return new UsernameNotFoundException("User not found");
                    });

            // 5. Generate new access and refresh tokens
            log.info("Generating new tokens for user: {}", username);
            AuthenticatedUser result = userService.loginInfo(user);

            log.info("Token refresh successful for user: {}", username);
            return result;

        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Token refresh failed: " + e.getMessage());
        }
    }
}