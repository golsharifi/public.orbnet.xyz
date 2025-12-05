package com.orbvpn.api.service.notification;

import com.orbvpn.api.domain.dto.MessageTemplateInput;
import com.orbvpn.api.domain.entity.MessageTemplate;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.MessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageTemplateService {
    private final MessageSource messageSource;
    private final MessageTemplateRepository templateRepository;

    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\{([^}]+)}");

    /**
     * Gets a localized template message from message source
     */
    public String getTemplate(String templateKey, User user, Object... args) {
        Locale locale = user.getProfile() != null && user.getProfile().getLanguage() != null
                ? user.getProfile().getLanguage()
                : Locale.ENGLISH;

        return messageSource.getMessage(templateKey, args, locale);
    }

    /**
     * Gets channel-specific variants of a template
     */
    public Map<String, String> getTemplateWithVariants(String templateKey, User user, Object... args) {
        Map<String, String> variants = new HashMap<>();

        // Get base template
        variants.put("default", getTemplate(templateKey, user, args));

        // Get WhatsApp specific template if exists
        try {
            variants.put("whatsapp", getTemplate(templateKey + ".whatsapp", user, args));
        } catch (Exception e) {
            variants.put("whatsapp", variants.get("default"));
        }

        // Get Telegram specific template if exists
        try {
            variants.put("telegram", getTemplate(templateKey + ".telegram", user, args));
        } catch (Exception e) {
            variants.put("telegram", variants.get("default"));
        }

        // Get SMS specific template if exists
        try {
            variants.put("sms", getTemplate(templateKey + ".sms", user, args));
        } catch (Exception e) {
            variants.put("sms", variants.get("default"));
        }

        return variants;
    }

    /**
     * Gets a custom template by ID
     */
    @Transactional(readOnly = true)
    public MessageTemplate getTemplate(String id) {
        try {
            return templateRepository.findById(Long.parseLong(id))
                    .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        } catch (NumberFormatException e) {
            // Try to find by name if ID parsing fails
            return templateRepository.findByName(id)
                    .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        }
    }

    /**
     * Gets all custom templates
     */
    @Transactional(readOnly = true)
    public List<MessageTemplate> getAllTemplates() {
        return templateRepository.findAll();
    }

    /**
     * Creates a new custom template
     */
    @Transactional
    public MessageTemplate createTemplate(MessageTemplateInput input) {
        validateTemplate(input);

        MessageTemplate template = new MessageTemplate();
        template.setName(input.getName());
        template.setContent(input.getContent());
        template.setVariables(extractVariables(input.getContent()));
        return templateRepository.save(template);
    }

    /**
     * Updates an existing custom template
     */
    @Transactional
    public MessageTemplate updateTemplate(String id, MessageTemplateInput input) {
        validateTemplate(input);

        MessageTemplate template = getTemplate(id);
        template.setName(input.getName());
        template.setContent(input.getContent());
        template.setVariables(extractVariables(input.getContent()));
        return templateRepository.save(template);
    }

    /**
     * Deletes a custom template
     */
    @Transactional
    public boolean deleteTemplate(String id) {
        templateRepository.deleteById(Long.parseLong(id));
        return true;
    }

    /**
     * Validates a template input
     */
    private void validateTemplate(MessageTemplateInput input) {
        if (input.getName() == null || input.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Template name is required");
        }
        if (input.getContent() == null || input.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Template content is required");
        }
    }

    /**
     * Extracts variables from template content
     */
    private List<String> extractVariables(String content) {
        List<String> variables = new ArrayList<>();
        Matcher matcher = TEMPLATE_VARIABLE_PATTERN.matcher(content);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    /**
     * Processes a template with actual values
     */
    public String processTemplate(MessageTemplate template, Map<String, Object> variables) {
        String content = template.getContent();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            content = content.replace("{" + entry.getKey() + "}",
                    entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return content;
    }
}