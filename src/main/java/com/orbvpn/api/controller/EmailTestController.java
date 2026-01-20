package com.orbvpn.api.controller;

import com.orbvpn.api.service.notification.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

/**
 * Controller for testing email templates.
 * Sends sample emails with test data to verify template rendering.
 *
 * Endpoints:
 * - POST /api/email-test/send-all?email=xxx - Send all test emails (Admin only)
 * - POST /api/email-test/send/{templateName}?email=xxx - Send single template (Admin only)
 * - GET /api/email-test/preview-all?email=xxx&token=orbvpn2024 - Preview/send all (with token for testing)
 */
@RestController
@RequestMapping("/api/email-test")
@RequiredArgsConstructor
@Slf4j
public class EmailTestController {

    private final EmailService emailService;

    private static final String TEST_TOKEN = "orbvpn2024test";

    /**
     * Send all test emails (requires admin authentication)
     */
    @Secured(ADMIN)
    @PostMapping("/send-all")
    public ResponseEntity<Map<String, Object>> sendAllTestEmails(@RequestParam String email) {
        log.info("Sending all test emails to: {}", email);

        Map<String, Object> results = new HashMap<>();
        AtomicInteger sent = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        Locale locale = Locale.ENGLISH;
        LocalDateTime expirationDateTime = LocalDateTime.now().plusDays(30);
        String formattedExpiry = expirationDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 1. Welcome New User
        sendTestEmail("welcome-new-user", email, locale, Map.of(
                "userName", "Nima",
                "username", "nima@example.com",
                "password", "SecurePass123!",
                "subscription", true,
                "duration", 30,
                "devices", 5,
                "formattedExpiry", formattedExpiry
        ), sent, failed, results);

        // 2. Welcome New User (No Subscription)
        sendTestEmail("welcome-new-user-no-subscription", email, locale, Map.of(
                "userName", "Nima",
                "username", "nima@example.com",
                "password", "SecurePass123!"
        ), sent, failed, results);

        // 3. Email Verification
        sendTestEmail("email-verification", email, locale, Map.of(
                "userName", "Nima",
                "token", "847293"
        ), sent, failed, results);

        // 4. Email Verification Success
        sendTestEmail("email-verification-success", email, locale, Map.of(
                "userName", "Nima"
        ), sent, failed, results);

        // 5. Password Reset
        sendTestEmail("password-reset", email, locale, Map.of(
                "userName", "Nima",
                "token", "928475",
                "mailtoLink", "mailto:support@orbvpn.com"
        ), sent, failed, results);

        // 6. Password Reset Done
        sendTestEmail("password-reset-done", email, locale, Map.of(
                "userName", "Nima",
                "mailtoLink", "mailto:support@orbvpn.com"
        ), sent, failed, results);

        // 7. Password Reset Admin
        sendTestEmail("password-reset-admin", email, locale, Map.of(
                "userName", "Nima",
                "newPassword", "NewSecurePass456!"
        ), sent, failed, results);

        // 8. Password Reencryption
        sendTestEmail("password-reencryption", email, locale, Map.of(
                "userName", "Nima",
                "newPassword", "ReencryptedPass789!"
        ), sent, failed, results);

        // 9. Email Reset Admin
        sendTestEmail("email-reset-admin", email, locale, Map.of(
                "userName", "Nima",
                "newEmail", "newemail@example.com"
        ), sent, failed, results);

        // 10. Magic Login
        sendTestEmail("magic-login", email, locale, Map.of(
                "userName", "Nima",
                "code", "738291"
        ), sent, failed, results);

        // 11. Subscription Renewal
        sendTestEmail("subscription-renewal", email, locale, Map.of(
                "userName", "Nima",
                "planName", "Premium VPN",
                "duration", 30,
                "multiLogin", 5,
                "expirationDate", expirationDateTime
        ), sent, failed, results);

        // 12. Subscription Renewal Confirmation
        sendTestEmail("subscription-renewal-confirmation", email, locale, Map.of(
                "userName", "Nima",
                "planName", "Premium VPN",
                "duration", 30,
                "devices", 5,
                "formattedExpiry", formattedExpiry,
                "amount", "$9.99"
        ), sent, failed, results);

        // 13. Subscription Expiry Reminder
        sendTestEmail("subscription-expiry-reminder", email, locale, Map.of(
                "userName", "Nima",
                "daysRemaining", 3,
                "expirationDate", expirationDateTime
        ), sent, failed, results);

        // 14. Subscription Expired
        sendTestEmail("subscription-expired", email, locale, Map.of(
                "userName", "Nima",
                "daysSinceExpiry", 2
        ), sent, failed, results);

        // 15. Birthday Wish
        sendTestEmail("birthday-wish", email, locale, Map.of(
                "userName", "Nima"
        ), sent, failed, results);

        // 16. Invoice Email
        sendTestEmail("invoice-email", email, locale, Map.of(
                "userName", "Nima",
                "invoiceNumber", "INV-2024-001234",
                "amount", "$9.99",
                "planName", "Premium VPN",
                "date", timestamp
        ), sent, failed, results);

        // 17. Gift Card Redemption
        sendTestEmail("gift-card-redemption", email, locale, Map.of(
                "userName", "Nima",
                "code", "GIFT-ABC123",
                "planName", "Premium VPN",
                "expirationDate", expirationDateTime
        ), sent, failed, results);

        // 18. Gift Card Cancellation
        sendTestEmail("gift-card-cancellation", email, locale, Map.of(
                "userName", "Nima",
                "code", "GIFT-ABC123",
                "planName", "Premium VPN",
                "cancelledAt", timestamp
        ), sent, failed, results);

        // 19. Extra Logins Confirmation
        sendTestEmail("extra-logins-confirmation", email, locale, Map.of(
                "userName", "Nima",
                "planName", "Extra 5 Logins",
                "count", 5,
                "expirationDate", expirationDateTime
        ), sent, failed, results);

        // 20. Extra Logins Expiration
        sendTestEmail("extra-logins-expiration", email, locale, Map.of(
                "userName", "Nima",
                "planName", "Extra 5 Logins",
                "count", 5,
                "expirationDate", expirationDateTime,
                "daysRemaining", 3
        ), sent, failed, results);

        // 21. Extra Logins Expired
        sendTestEmail("extra-logins-expired", email, locale, Map.of(
                "userName", "Nima",
                "planName", "Extra 5 Logins",
                "count", 5,
                "expirationDate", expirationDateTime
        ), sent, failed, results);

        // 22. Extra Logins Gift Sent
        sendTestEmail("extra-logins-gift-sent", email, locale, Map.of(
                "userName", "Nima",
                "recipientEmail", "friend@example.com",
                "planName", "Extra 3 Logins",
                "count", 3,
                "expirationDate", expirationDateTime
        ), sent, failed, results);

        // 23. Extra Logins Gift Received
        sendTestEmail("extra-logins-gift-received", email, locale, Map.of(
                "userName", "Nima",
                "senderEmail", "john@example.com",
                "planName", "Extra 3 Logins",
                "count", 3,
                "expirationDate", expirationDateTime
        ), sent, failed, results);

        // 24. Token Code
        sendTestEmail("token-code", email, locale, Map.of(
                "userName", "Nima",
                "token", "DISCOUNT50"
        ), sent, failed, results);

        // 25. System Notification
        sendTestEmail("system-notification", email, locale, Map.of(
                "userName", "Nima",
                "subject", "Scheduled Maintenance",
                "message", "We will be performing scheduled maintenance on our servers on Saturday, December 14th from 2:00 AM to 4:00 AM UTC. During this time, the service may be temporarily unavailable. We apologize for any inconvenience."
        ), sent, failed, results);

        // 26. Admin Alert
        sendTestEmail("admin-alert", email, locale, Map.of(
                "userName", "Admin",
                "title", "High CPU Usage Alert",
                "message", "Server US-EAST-1 is experiencing high CPU usage (95%). Please investigate immediately.",
                "timestamp", timestamp
        ), sent, failed, results);

        // 27. Payment Failed
        sendTestEmail("payment-failed", email, locale, Map.of(
                "userName", "Nima",
                "planName", "Premium VPN"
        ), sent, failed, results);

        results.put("totalSent", sent.get());
        results.put("totalFailed", failed.get());
        results.put("email", email);

        log.info("Finished sending test emails. Sent: {}, Failed: {}", sent.get(), failed.get());
        return ResponseEntity.ok(results);
    }

    @Secured(ADMIN)
    @PostMapping("/send/{templateName}")
    public ResponseEntity<Map<String, Object>> sendSingleTestEmail(
            @PathVariable String templateName,
            @RequestParam String email) {
        log.info("Sending test email '{}' to: {}", templateName, email);

        Map<String, Object> results = new HashMap<>();
        AtomicInteger sent = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        Locale locale = Locale.ENGLISH;
        String formattedExpiry = LocalDate.now().plusDays(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        Map<String, Object> variables = getTestVariables(templateName, formattedExpiry, timestamp);
        sendTestEmail(templateName, email, locale, variables, sent, failed, results);

        results.put("email", email);
        return ResponseEntity.ok(results);
    }

    /**
     * Send all test emails with token-based authentication (for testing without login)
     * Usage: GET /api/email-test/run?email=nima@golsharifi.com&token=orbvpn2024test
     */
    @GetMapping("/run")
    public ResponseEntity<Map<String, Object>> sendTestEmailsWithToken(
            @RequestParam String email,
            @RequestParam String token) {

        if (!TEST_TOKEN.equals(token)) {
            log.warn("Invalid test token provided for email test");
            return ResponseEntity.status(403).body(Map.of("error", "Invalid token"));
        }

        log.info("Sending all test emails to {} via token auth", email);
        return sendAllTestEmails(email);
    }

    private void sendTestEmail(String templateName, String email, Locale locale,
                               Map<String, Object> variables, AtomicInteger sent,
                               AtomicInteger failed, Map<String, Object> results) {
        try {
            emailService.sendTemplatedEmail(email, templateName, variables, locale);
            results.put(templateName, "sent");
            sent.incrementAndGet();
            log.info("Successfully sent test email: {}", templateName);
            // Add small delay to avoid overwhelming the mail server
            Thread.sleep(1000);
        } catch (Exception e) {
            results.put(templateName, "failed: " + e.getMessage());
            failed.incrementAndGet();
            log.error("Failed to send test email '{}': {}", templateName, e.getMessage());
        }
    }

    private Map<String, Object> getTestVariables(String templateName, String formattedExpiry, String timestamp) {
        return switch (templateName) {
            case "welcome-new-user" -> Map.of(
                    "userName", "Nima",
                    "username", "nima@example.com",
                    "password", "SecurePass123!",
                    "subscription", true,
                    "duration", 30,
                    "devices", 5,
                    "formattedExpiry", formattedExpiry
            );
            case "password-reset" -> Map.of(
                    "userName", "Nima",
                    "token", "928475",
                    "mailtoLink", "mailto:support@orbvpn.com"
            );
            case "email-verification" -> Map.of(
                    "userName", "Nima",
                    "token", "847293"
            );
            default -> Map.of(
                    "userName", "Nima",
                    "message", "This is a test notification.",
                    "timestamp", timestamp
            );
        };
    }
}
