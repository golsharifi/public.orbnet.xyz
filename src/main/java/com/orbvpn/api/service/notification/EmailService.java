package com.orbvpn.api.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
        private final JavaMailSender mailSender;
        private final TemplateEngine templateEngine;
        private final MessageSource messageSource;

        public void sendTemplatedEmail(String toEmail, String templateName,
                        Map<String, Object> variables, Locale locale) {
                log.debug("Sending templated email '{}' to: {} with locale: {}",
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
                        helper.addInline("whatsapp-icon", new ClassPathResource("/image/whatsapp-icon.png"));
                        helper.addInline("telegram-icon", new ClassPathResource("/image/telegram-icon.png"));
                        helper.addInline("instagram-icon", new ClassPathResource("/image/instagram-icon.png"));

                        mailSender.send(message);
                        log.info("Successfully sent templated email '{}' to: {} with locale: {}",
                                        templateName, toEmail, locale);
                } catch (Exception e) {
                        log.error("Failed to send templated email to: {} with locale: {}",
                                        toEmail, locale, e);
                        throw new RuntimeException("Failed to send email", e);
                }
        }
}