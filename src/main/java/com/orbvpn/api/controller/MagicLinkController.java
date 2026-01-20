package com.orbvpn.api.controller;

import com.orbvpn.api.domain.dto.AuthenticatedUser;
import com.orbvpn.api.service.MagicLoginService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * REST Controller for handling magic link authentication.
 * Users click a link in their email and are authenticated automatically.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class MagicLinkController {

    private final MagicLoginService magicLoginService;

    @Value("${app.frontend.url:https://orbvpn.com}")
    private String frontendUrl;

    /**
     * Verify magic link and redirect with tokens.
     * This endpoint is called when users click the magic link in their email.
     *
     * @param token the magic link token from the URL
     * @param request the HTTP request
     * @param response the HTTP response
     * @return ResponseEntity with redirect or error HTML
     */
    @GetMapping("/auth/magic-link")
    public ResponseEntity<?> verifyMagicLink(
            @RequestParam("token") String token,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        log.info("Magic link verification request received");

        try {
            AuthenticatedUser authenticatedUser = magicLoginService.verifyMagicLink(token);

            // Check if the request is from a mobile app
            if (isMobile(request)) {
                // For mobile app, use custom URL scheme - include both access and refresh tokens
                String redirectUrl = String.format("orbvpn://login?token=%s&refreshToken=%s",
                        URLEncoder.encode(authenticatedUser.getAccessToken(), StandardCharsets.UTF_8),
                        authenticatedUser.getRefreshToken() != null
                                ? URLEncoder.encode(authenticatedUser.getRefreshToken(), StandardCharsets.UTF_8)
                                : "");

                response.setContentType("text/html;charset=UTF-8");
                String html = String.format(
                        "<html><head><meta charset=\"UTF-8\"></head><body>" +
                        "<script>window.location.href = '%s';</script>" +
                        "<p>Redirecting to OrbVPN app...</p>" +
                        "<p>If you are not redirected, <a href=\"%s\">click here</a>.</p>" +
                        "</body></html>",
                        redirectUrl, redirectUrl);

                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(html);
            } else {
                // For web app, redirect to frontend with tokens
                String redirectUrl = buildWebRedirectUrl(authenticatedUser.getAccessToken(),
                        authenticatedUser.getRefreshToken());
                response.sendRedirect(redirectUrl);
                return ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(redirectUrl))
                        .build();
            }
        } catch (Exception e) {
            log.error("Magic link verification failed: {}", e.getMessage());

            // Redirect to error page with message
            String errorUrl = frontendUrl + "/login?error=" +
                    URLEncoder.encode("Invalid or expired magic link. Please request a new one.", StandardCharsets.UTF_8);

            if (isMobile(request)) {
                String mobileErrorUrl = "orbvpn://login?error=" +
                        URLEncoder.encode("Invalid or expired magic link. Please request a new one.", StandardCharsets.UTF_8);
                response.setContentType("text/html;charset=UTF-8");
                String html = String.format(
                        "<html><head><meta charset=\"UTF-8\"></head><body>" +
                        "<script>window.location.href = '%s';</script>" +
                        "<p>Redirecting to OrbVPN app...</p>" +
                        "</body></html>",
                        mobileErrorUrl);
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(html);
            }

            response.sendRedirect(errorUrl);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(errorUrl))
                    .build();
        }
    }

    /**
     * Build the web redirect URL with tokens
     */
    private String buildWebRedirectUrl(String accessToken, String refreshToken) {
        StringBuilder url = new StringBuilder(frontendUrl);

        if (!frontendUrl.endsWith("/")) {
            url.append("/");
        }

        url.append("?token=").append(URLEncoder.encode(accessToken, StandardCharsets.UTF_8));
        if (refreshToken != null && !refreshToken.isEmpty()) {
            url.append("&refreshToken=").append(URLEncoder.encode(refreshToken, StandardCharsets.UTF_8));
        }

        return url.toString();
    }

    /**
     * Check if the request is from a mobile device
     */
    private boolean isMobile(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return false;
        }
        return userAgent.toLowerCase().contains("mobi");
    }
}
