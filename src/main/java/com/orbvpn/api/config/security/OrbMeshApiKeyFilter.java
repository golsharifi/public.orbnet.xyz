package com.orbvpn.api.config.security;

import com.orbvpn.api.service.OrbMeshService;
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
public class OrbMeshApiKeyFilter extends OncePerRequestFilter {

    private final OrbMeshService orbmeshService;
    private static final String API_KEY_HEADER = "X-OrbMesh-API-Key";
    private static final String LEGACY_API_KEY_HEADER = "X-OrbX-API-Key";  // Backward compatibility
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Check for API key in new header first, then fall back to legacy header
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null) {
            apiKey = request.getHeader(LEGACY_API_KEY_HEADER);  // Backward compatibility
        }

        // If not in custom header, check Authorization header for Bearer token
        if (apiKey == null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                String token = authHeader.substring(BEARER_PREFIX.length());
                // Accept both new format (orbmesh_...) and legacy format (alphanumeric keys)
                if (isOrbMeshApiKey(token)) {
                    apiKey = token;
                }
            }
        }

        // Validate API key if present
        if (apiKey != null && isOrbMeshApiKey(apiKey)) {
            try {
                if (orbmeshService.validateApiKey(apiKey)) {
                    log.debug("Valid OrbMesh API key authentication");

                    // Create authentication token with ORBMESH_SERVER authority
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            "orbmesh-server",
                            null,
                            List.of(new SimpleGrantedAuthority("ORBMESH_SERVER")));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    log.warn("❌ Invalid OrbMesh API key attempted");
                }
            } catch (Exception e) {
                log.error("Error validating OrbMesh API key", e);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Check if a token looks like an OrbMesh API key.
     * Accepts new format (orbmesh_...), old format (orbx_...), and legacy format (alphanumeric, 16+ chars).
     */
    private boolean isOrbMeshApiKey(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        // New format: starts with orbmesh_
        if (token.startsWith("orbmesh_")) {
            return true;
        }
        // Old format: starts with orbx_ (backward compatibility)
        if (token.startsWith("orbx_")) {
            return true;
        }
        // Legacy format: alphanumeric key, 16+ characters, not a JWT (which contains dots)
        return token.length() >= 16 && !token.contains(".") && token.matches("^[a-zA-Z0-9_-]+$");
    }
}