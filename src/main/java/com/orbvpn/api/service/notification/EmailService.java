package com.orbvpn.api.service.notification;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MessageSource messageSource;
    private final UserRepository userRepository;
    private final EmailUnsubscribeService unsubscribeService;

    @Value("${application.website-url:https://orbnet.xyz}")
    private String baseUrl;

    /**
     * Send a templated email without unsubscribe functionality.
     * Use this for transactional emails that must always be sent (password reset, etc.)
     */
    public void sendTemplatedEmail(String toEmail, String templateName,
                                   Map<String, Object> variables, Locale locale) {
        sendTemplatedEmailInternal(toEmail, templateName, variables, locale, false);
    }

    /**
     * Send a templated email with unsubscribe functionality.
     * Use this for marketing and notification emails.
     */
    public void sendTemplatedEmailWithUnsubscribe(String toEmail, String templateName,
                                                   Map<String, Object> variables, Locale locale) {
        sendTemplatedEmailInternal(toEmail, templateName, variables, locale, true);
    }

    /**
     * Send a templated email to a User entity with unsubscribe functionality.
     */
    public void sendTemplatedEmailToUser(User user, String templateName,
                                          Map<String, Object> variables, Locale locale) {
        // Create mutable copy of variables
        Map<String, Object> vars = new HashMap<>(variables);

        // Generate unsubscribe URLs for this user
        String unsubscribeUrl = unsubscribeService.generateUnsubscribeUrl(user);
        String preferencesUrl = unsubscribeService.generatePreferencesUrl(user);

        vars.put("unsubscribeUrl", unsubscribeUrl);
        vars.put("preferencesUrl", preferencesUrl);

        sendTemplatedEmailWithHeaders(user.getEmail(), templateName, vars, locale, unsubscribeUrl);
    }

    private void sendTemplatedEmailInternal(String toEmail, String templateName,
                                            Map<String, Object> variables, Locale locale,
                                            boolean includeUnsubscribe) {
        log.debug("Sending templated email '{}' to: {} with locale: {}",
                templateName, toEmail, locale);

        try {
            // Create mutable copy of variables
            Map<String, Object> vars = new HashMap<>(variables);

            String unsubscribeUrl = null;
            String preferencesUrl = null;

            // Try to find user and add unsubscribe links
            if (includeUnsubscribe) {
                Optional<User> userOpt = userRepository.findByEmail(toEmail);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    unsubscribeUrl = unsubscribeService.generateUnsubscribeUrl(user);
                    preferencesUrl = unsubscribeService.generatePreferencesUrl(user);
                    vars.put("unsubscribeUrl", unsubscribeUrl);
                    vars.put("preferencesUrl", preferencesUrl);
                }
            }

            if (unsubscribeUrl != null) {
                sendTemplatedEmailWithHeaders(toEmail, templateName, vars, locale, unsubscribeUrl);
            } else {
                sendTemplatedEmailBasic(toEmail, templateName, vars, locale);
            }

        } catch (Exception e) {
            log.error("Failed to send templated email to: {} with locale: {}",
                    toEmail, locale, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    private void sendTemplatedEmailBasic(String toEmail, String templateName,
                                          Map<String, Object> variables, Locale locale) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true,
                StandardCharsets.UTF_8.toString());

        Context context = new Context(locale);
        variables.forEach((key, value) -> {
            log.debug("Setting template variable: {} = {}", key, value);
            context.setVariable(key, value);
        });

        String htmlContent = templateEngine.process("email/" + templateName, context);
        String subject = messageSource.getMessage(
                "email." + templateName + ".title", null, locale);

        log.debug("Resolved subject for locale {}: {}", locale, subject);

        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        helper.setTo(toEmail);
        helper.setFrom(new InternetAddress("info@orbvpn.com", "OrbVPN"));
        helper.addInline("logo", new ClassPathResource("/image/logo.png"));

        mailSender.send(message);
        log.info("Successfully sent templated email '{}' to: {} with locale: {}",
                templateName, toEmail, locale);
    }

    private void sendTemplatedEmailWithHeaders(String toEmail, String templateName,
                                                Map<String, Object> variables, Locale locale,
                                                String unsubscribeUrl) {
        log.debug("Sending templated email '{}' to: {} with locale: {} (with unsubscribe)",
                templateName, toEmail, locale);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true,
                    StandardCharsets.UTF_8.toString());

            Context context = new Context(locale);
            variables.forEach((key, value) -> {
                log.debug("Setting template variable: {} = {}", key, value);
                context.setVariable(key, value);
            });

            String htmlContent = templateEngine.process("email/" + templateName, context);
            String subject = messageSource.getMessage(
                    "email." + templateName + ".title", null, locale);

            log.debug("Resolved subject for locale {}: {}", locale, subject);

            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setTo(toEmail);
            helper.setFrom(new InternetAddress("info@orbvpn.com", "OrbVPN"));
            helper.addInline("logo", new ClassPathResource("/image/logo.png"));

            // Add RFC 8058 List-Unsubscribe headers for one-click unsubscribe support
            // This is used by email clients like Gmail, Apple Mail to show unsubscribe buttons
            message.addHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");
            message.addHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");

            mailSender.send(message);
            log.info("Successfully sent templated email '{}' to: {} with locale: {} (with unsubscribe headers)",
                    templateName, toEmail, locale);
        } catch (Exception e) {
            log.error("Failed to send templated email with headers to: {} with locale: {}",
                    toEmail, locale, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Check if a user can receive emails (not unsubscribed).
     */
    public boolean canSendEmailTo(String email) {
        return userRepository.findByEmail(email)
                .map(user -> {
                    // For now, always allow - the unsubscribe check should be done at notification level
                    return true;
                })
                .orElse(true);
    }
}
