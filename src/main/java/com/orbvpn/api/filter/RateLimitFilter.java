package com.orbvpn.api.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;
import com.orbvpn.api.service.TokenRateLimiterService;
import com.orbvpn.api.service.IPService;

import jakarta.servlet.http.HttpServletRequest;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.ORBMESH_SERVER;
import static com.orbvpn.api.domain.enums.RoleName.Constants.RESELLER;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

public class RateLimitFilter extends OncePerRequestFilter {

    @Autowired
    private UserRateLimiter rateLimiter;

    @Autowired
    private TokenRateLimiterService tokenRateLimiterService;

    @Autowired
    private IPService ipService;

    @Override
    protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            jakarta.servlet.FilterChain filterChain)
            throws jakarta.servlet.ServletException, java.io.IOException {

        String clientIp = request.getRemoteAddr();

        // Whitelist and Blacklist Checks
        if (ipService.isIPWhitelisted(clientIp)) {
            filterChain.doFilter(request, response); // Allow whitelisted IPs without further checks
            return;
        }

        if (ipService.isIPBlacklisted(clientIp)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.getWriter().write("This IP address is blacklisted.");
            return;
        }

        // Check if there's an Authorization header before applying rate limiting checks
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ NEW: Skip rate limiting for OrbMesh servers (server-to-server communication)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getAuthorities() != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                if (ORBMESH_SERVER.equals(authority.getAuthority())) {
                    // OrbMesh servers are trusted - skip all rate limiting
                    filterChain.doFilter(request, response);
                    return;
                }
            }
        }

        // IP Based Rate Limiting
        if (!rateLimiter.isAllowedForIp(clientIp)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too many requests from this IP address");
            return;
        }

        // User Based Rate Limiting
        String userId = retrieveUserIdFromRequest(request);
        String roleName = retrieveRoleFromRequest();
        if (!rateLimiter.isAllowedForUser(userId, roleName)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("User has exceeded their rate limit");
            return;
        }

        // Token Based Rate Limiting
        String token = extractTokenFromRequest(request);
        if (!tokenRateLimiterService.isAllowed(token, roleName)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too many requests from this token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String retrieveUserIdFromRequest(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("No authentication data found");
        }

        // ✅ UPDATED: Handle both UserDetails and simple String principals
        if (authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userDetails.getUsername();
        } else if (authentication.getPrincipal() instanceof String) {
            // Handle OrbMesh server authentication (principal is just a string)
            return (String) authentication.getPrincipal();
        }

        throw new IllegalStateException("User details not found in JWT");
    }

    private String retrieveRoleFromRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getAuthorities() != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                if (ADMIN.equals(authority.getAuthority())
                        || RESELLER.equals(authority.getAuthority())
                        || USER.equals(authority.getAuthority())
                        || ORBMESH_SERVER.equals(authority.getAuthority())) {
                    return authority.getAuthority();
                }
            }
        }

        throw new IllegalStateException("User role not found in JWT");
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}