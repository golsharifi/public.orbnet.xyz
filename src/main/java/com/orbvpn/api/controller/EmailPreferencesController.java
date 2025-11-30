package com.orbvpn.api.controller;

import com.orbvpn.api.config.security.Unsecured;
import com.orbvpn.api.domain.enums.NotificationCategory;
import com.orbvpn.api.service.notification.EmailUnsubscribeService;
import com.orbvpn.api.service.notification.EmailUnsubscribeService.PreferencesView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for the email preferences management web page.
 * Allows users to manage their notification preferences via a web interface.
 */
@Controller
@RequestMapping("/email")
@RequiredArgsConstructor
@Slf4j
public class EmailPreferencesController {

    private final EmailUnsubscribeService unsubscribeService;

    /**
     * Display the preferences management page.
     */
    @GetMapping("/preferences")
    @Unsecured
    public String showPreferencesPage(
            @RequestParam String token,
            Model model) {

        Optional<PreferencesView> prefsOpt = unsubscribeService.getPreferencesForToken(token);

        if (prefsOpt.isEmpty()) {
            model.addAttribute("error", "Invalid or expired link. Please use a recent email to access your preferences.");
            return "email/preferences-error";
        }

        PreferencesView prefs = prefsOpt.get();
        model.addAttribute("email", maskEmail(prefs.email()));
        model.addAttribute("emailUnsubscribed", prefs.emailUnsubscribed());
        model.addAttribute("enabledCategories", prefs.enabledCategories());
        model.addAttribute("allCategories", NotificationCategory.values());
        model.addAttribute("token", token);

        return "email/preferences";
    }

    /**
     * Process preferences update form submission.
     */
    @PostMapping("/preferences")
    @Unsecured
    public String updatePreferences(
            @RequestParam String token,
            @RequestParam(required = false, defaultValue = "false") boolean emailEnabled,
            @RequestParam(required = false) List<String> categories,
            Model model) {

        Set<NotificationCategory> enabledCategories = categories != null
                ? categories.stream()
                    .map(NotificationCategory::valueOf)
                    .collect(Collectors.toSet())
                : new HashSet<>();

        boolean success = unsubscribeService.updatePreferencesViaToken(token, enabledCategories, emailEnabled);

        if (success) {
            model.addAttribute("success", true);
            model.addAttribute("message", "Your email preferences have been updated successfully.");
        } else {
            model.addAttribute("success", false);
            model.addAttribute("message", "Failed to update preferences. The link may have expired.");
        }

        // Reload preferences to show current state
        Optional<PreferencesView> prefsOpt = unsubscribeService.getPreferencesForToken(token);
        if (prefsOpt.isPresent()) {
            PreferencesView prefs = prefsOpt.get();
            model.addAttribute("email", maskEmail(prefs.email()));
            model.addAttribute("emailUnsubscribed", prefs.emailUnsubscribed());
            model.addAttribute("enabledCategories", prefs.enabledCategories());
            model.addAttribute("allCategories", NotificationCategory.values());
            model.addAttribute("token", token);
        }

        return "email/preferences";
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
