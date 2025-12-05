package com.orbvpn.api.config.security;

import com.orbvpn.api.service.OrbXService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrbXApiKeyFilter extends OncePerRequestFilter {

    private final OrbXService orbxService;
    private static final String API_KEY_HEADER = "X-OrbX-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Check for API key in custom header
        String apiKey = request.getHeader(API_KEY_HEADER);

        // If not in custom header, check Authorization header for Bearer token
        if (apiKey == null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                String token = authHeader.substring(BEARER_PREFIX.length());
                // Accept both new format (orbx_...) and legacy format (alphanumeric keys)
                if (isOrbXApiKey(token)) {
                    apiKey = token;
                }
            }
        }

        // Validate API key if present
        if (apiKey != null && isOrbXApiKey(apiKey)) {
            try {
                if (orbxService.validateApiKey(apiKey)) {
                    log.debug("✅ Valid OrbX API key authentication");

                    // Create authentication token with ORBX_SERVER authority
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            "orbx-server",
                            null,
                            List.of(new SimpleGrantedAuthority("ORBX_SERVER")));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    log.warn("❌ Invalid OrbX API key attempted");
                }
            } catch (Exception e) {
                log.error("Error validating OrbX API key", e);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Check if a token looks like an OrbX API key.
     * Accepts both new format (orbx_...) and legacy format (alphanumeric, 16+ chars).
     */
    private boolean isOrbXApiKey(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        // New format: starts with orbx_
        if (token.startsWith("orbx_")) {
            return true;
        }
        // Legacy format: alphanumeric key, 16+ characters, not a JWT (which contains dots)
        return token.length() >= 16 && !token.contains(".") && token.matches("^[a-zA-Z0-9_-]+$");
    }
}