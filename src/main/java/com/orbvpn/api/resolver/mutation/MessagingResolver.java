package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.UserProfileView;
import com.orbvpn.api.domain.entity.MessageTemplate;
import com.orbvpn.api.domain.entity.TelegramMessage;
import com.orbvpn.api.domain.entity.MessageDeliveryStatus;
import com.orbvpn.api.domain.dto.MessageTemplateInput;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.TelegramMessageRepository;
import com.orbvpn.api.service.UserProfileService;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.notification.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MessagingResolver {
    private final MessageTemplateService messageTemplateService;
    private final MessageTrackingService messageTrackingService;
    private final MessageRateLimiter rateLimiter;
    private final UserProfileService userProfileService;
    private final WhatsAppService whatsAppService;
    private final TelegramService telegramService;
    private final MessageQueueService messageQueueService;
    private final UserService userService;
    private final TelegramMessageRepository messageRepository;

    @Secured(ADMIN)
    @QueryMapping
    public List<MessageTemplate> getMessageTemplates() {
        log.info("Fetching all message templates");
        return messageTemplateService.getAllTemplates();
    }

    @Secured(ADMIN)
    @QueryMapping
    public MessageTemplate getMessageTemplate(@Argument String id) {
        log.info("Fetching message template: {}", id);
        return messageTemplateService.getTemplate(id);
    }

    @QueryMapping
    public MessageDeliveryStatus getMessageStatus(@Argument String messageId) {

        log.info("Fetching message status for ID: {}", messageId);
        return messageTrackingService.getStatus(messageId);
    }

    @QueryMapping
    public List<MessageDeliveryStatus> getUserMessageHistory(@Argument String userId) {
        log.info("Fetching message history for user: {}", userId);
        return messageTrackingService.getUserHistory(userId);
    }

    @QueryMapping
    public int getWhatsAppRateLimit(@Argument String phoneNumber) {
        return rateLimiter.getRemainingWhatsAppTokens(phoneNumber);
    }

    @QueryMapping
    public int getTelegramRateLimit(@Argument String chatId) {
        return rateLimiter.getRemainingTelegramTokens(chatId);
    }

    @Secured(ADMIN)
    @MutationMapping
    public MessageTemplate createMessageTemplate(@Argument MessageTemplateInput input) {
        log.info("Creating new message template: {}", input.getName());
        return messageTemplateService.createTemplate(input);
    }

    @Secured(ADMIN)
    @MutationMapping
    public MessageTemplate updateMessageTemplate(@Argument String id, @Argument MessageTemplateInput input) {
        log.info("Updating message template: {}", id);
        return messageTemplateService.updateTemplate(id, input);
    }

    @Secured(ADMIN)
    @MutationMapping
    public boolean deleteMessageTemplate(@Argument String id) {
        log.info("Deleting message template: {}", id);
        return messageTemplateService.deleteTemplate(id);
    }

    @MutationMapping
    public UserProfileView updateTelegramInfo(@Argument String username, @Argument String chatId) {
        log.info("Updating Telegram info for user with username: {}", username);
        return userProfileService.updateTelegramInfo(username, chatId);
    }

    @Secured(ADMIN)
    @MutationMapping
    public List<String> sendBulkMessage(
            @Argument List<String> userIds,
            @Argument String templateId,
            @Argument List<String> variables,
            @Argument List<String> channels) {
        log.info("Sending bulk message to {} users using template: {}", userIds.size(), templateId);
        return messageQueueService.queueBulkMessage(userIds, templateId, variables, channels);
    }

    @MutationMapping
    public boolean testWhatsAppConnection(@Argument String phoneNumber) {
        log.info("Testing WhatsApp connection for: {}", phoneNumber);
        return whatsAppService.testConnection(phoneNumber);
    }

    @MutationMapping
    public boolean testTelegramConnection(@Argument String chatId) {
        log.info("Testing Telegram connection for: {}", chatId);
        return telegramService.testConnection(chatId);
    }

    @QueryMapping
    public List<TelegramMessage> getTelegramMessagesByUser(
            @Argument String userId,
            @Argument Integer page,
            @Argument Integer size) {
        log.info("Fetching telegram messages for user: {}", userId);
        try {
            User user = userService.getUserById(Integer.parseInt(userId));
            PageRequest pageRequest = PageRequest.of(
                    page != null ? page : 0,
                    size != null ? size : 20,
                    Sort.by("timestamp").descending());

            Page<TelegramMessage> messages = messageRepository.findByUser(user, pageRequest);
            return messages.getContent();
        } catch (Exception e) {
            log.error("Error fetching telegram messages", e);
            // Return empty list instead of null to avoid GraphQL null error
            return new ArrayList<>();
        }
    }
}