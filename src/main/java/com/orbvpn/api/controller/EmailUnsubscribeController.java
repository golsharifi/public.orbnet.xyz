package com.orbvpn.api.controller;

import com.orbvpn.api.config.security.Unsecured;
import com.orbvpn.api.domain.enums.NotificationCategory;
import com.orbvpn.api.service.notification.EmailUnsubscribeService;
import com.orbvpn.api.service.notification.EmailUnsubscribeService.UnsubscribeResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Controller for handling email unsubscribe operations.
 * Provides:
 * - One-click unsubscribe endpoint (CAN-SPAM compliant)
 * - Preferences management page
 * - API endpoints for programmatic access
 */
@Controller
@RequestMapping("/api/email")
@RequiredArgsConstructor
@Slf4j
public class EmailUnsubscribeController {

    private final EmailUnsubscribeService unsubscribeService;

    /**
     * One-click unsubscribe endpoint.
     * Handles GET requests from email unsubscribe links.
     * Returns an HTML page confirming the unsubscribe.
     */
    @GetMapping("/unsubscribe")
    @Unsecured
    public String unsubscribe(
            @RequestParam String token,
            HttpServletRequest request,
            Model model,
            Locale locale) {

        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        UnsubscribeResult result = unsubscribeService.processUnsubscribe(token, ipAddress, userAgent);

        model.addAttribute("success", result.success());
        model.addAttribute("message", result.message());
        model.addAttribute("email", result.email());
        model.addAttribute("preferencesUrl", result.preferencesUrl());

        if (result.success()) {
            log.info("Successfully processed unsubscribe for email: {}", result.email());
        }

        return "email/unsubscribe-confirmation";
    }

    /**
     * POST endpoint for one-click unsubscribe (RFC 8058 List-Unsubscribe-Post).
     * Some email clients send POST requests for one-click unsubscribe.
     */
    @PostMapping("/unsubscribe")
    @Unsecured
    public String unsubscribePost(
            @RequestParam String token,
            @RequestParam(required = false, defaultValue = "One-Click") String listUnsubscribe,
            HttpServletRequest request,
            Model model,
            Locale locale) {
        return unsubscribe(token, request, model, locale);
    }

    /**
     * JSON API endpoint for unsubscribe.
     */
    @PostMapping(value = "/unsubscribe/api", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Unsecured
    public Map<String, Object> unsubscribeApi(
            @RequestParam String token,
            HttpServletRequest request) {

        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        UnsubscribeResult result = unsubscribeService.processUnsubscribe(token, ipAddress, userAgent);

        Map<String, Object> response = new HashMap<>();
        response.put("success", result.success());
        response.put("message", result.message());
        if (result.email() != null) {
            response.put("email", maskEmail(result.email()));
        }
        if (result.preferencesUrl() != null) {
            response.put("preferencesUrl", result.preferencesUrl());
        }

        return response;
    }

    /**
     * Resubscribe endpoint.
     */
    @GetMapping("/resubscribe")
    @Unsecured
    public String resubscribe(
            @RequestParam String token,
            Model model) {

        boolean success = unsubscribeService.processResubscribe(token);

        model.addAttribute("success", success);
        model.addAttribute("message", success
                ? "You have been resubscribed to email notifications."
                : "Invalid or expired link.");

        return "email/resubscribe-confirmation";
    }

    /**
     * Get available notification categories.
     */
    @GetMapping(value = "/categories", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Unsecured
    public List<Map<String, String>> getCategories() {
        return Arrays.stream(NotificationCategory.values())
                .map(cat -> Map.of(
                        "name", cat.name(),
                        "description", cat.getDescription()
                ))
                .toList();
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];

        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "***@" + domain;
        }
        return localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1) + "@" + domain;
    }
}
