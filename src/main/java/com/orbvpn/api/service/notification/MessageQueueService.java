package com.orbvpn.api.service.notification;

import com.orbvpn.api.domain.entity.MessageTemplate;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.event.MessageQueueEvent;
import com.orbvpn.api.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageQueueService {
    private final TelegramService telegramService;
    private final UserService userService;
    private final MessageTemplateService messageTemplateService;
    private final MessageRateLimiter rateLimiter;
    private final ApplicationEventPublisher eventPublisher;

    @Data
    private static class QueuedMessage {
        private final String recipient;
        private final String message;
        private final MessageQueueEvent.MessageType type;
        private final LocalDateTime createdAt;
        private int retryCount = 0;
        private static final int MAX_RETRIES = 3;

        public boolean canRetry() {
            return retryCount < MAX_RETRIES;
        }
    }

    private final Queue<QueuedMessage> messageQueue = new ConcurrentLinkedQueue<>();

    public void queueWhatsAppMessage(String phoneNumber, String message) {
        messageQueue.offer(new QueuedMessage(
                phoneNumber,
                message,
                MessageQueueEvent.MessageType.WHATSAPP,
                LocalDateTime.now()));
    }

    public void queueTelegramMessage(String chatId, String message) {
        messageQueue.offer(new QueuedMessage(
                chatId,
                message,
                MessageQueueEvent.MessageType.TELEGRAM,
                LocalDateTime.now()));
    }

    @Scheduled(fixedRate = 60000) // Process queue every minute
    public void processQueue() {
        QueuedMessage message;
        while ((message = messageQueue.poll()) != null) {
            try {
                switch (message.getType()) {
                    case WHATSAPP:
                        if (rateLimiter.tryConsumeWhatsApp(message.getRecipient())) {
                            eventPublisher.publishEvent(new MessageQueueEvent(
                                    this,
                                    message.getRecipient(),
                                    message.getMessage(),
                                    MessageQueueEvent.MessageType.WHATSAPP));
                        } else {
                            messageQueue.offer(message);
                            log.debug("Rate limit hit for WhatsApp recipient: {}", message.getRecipient());
                        }
                        break;
                    case TELEGRAM:
                        if (rateLimiter.tryConsumeTelegram(message.getRecipient())) {
                            telegramService.sendMessage(message.getRecipient(), message.getMessage());
                        } else {
                            messageQueue.offer(message);
                            log.debug("Rate limit hit for Telegram recipient: {}", message.getRecipient());
                        }
                        break;
                }
            } catch (Exception e) {
                log.error("Failed to send {} message to {}", message.getType(), message.getRecipient(), e);
                if (message.canRetry()) {
                    message.setRetryCount(message.getRetryCount() + 1);
                    messageQueue.offer(message);
                } else {
                    log.error("Message failed after max retries: {}", message);
                }
            }
        }
    }

    public List<String> queueBulkMessage(
            List<String> userIds,
            String templateId,
            List<String> variables,
            List<String> channels) {

        List<String> messageIds = new ArrayList<>();
        MessageTemplate template = messageTemplateService.getTemplate(templateId);

        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }

        for (String userId : userIds) {
            User user = userService.getUserById(Integer.parseInt(userId));
            // Fix template call by passing required arguments
            String messageContent = messageTemplateService.getTemplate(
                    templateId,
                    user,
                    variables != null ? variables.toArray() : new Object[0]);

            for (String channel : channels) {
                try {
                    String messageId = UUID.randomUUID().toString();
                    messageIds.add(messageId);

                    switch (channel.toUpperCase()) {
                        case "WHATSAPP":
                            if (user.getProfile().getPhone() != null) {
                                queueWhatsAppMessage(user.getProfile().getPhone(), messageContent);
                            }
                            break;

                        case "TELEGRAM":
                            if (user.getProfile().getTelegramChatId() != null) {
                                queueTelegramMessage(user.getProfile().getTelegramChatId(), messageContent);
                            }
                            break;

                        default:
                            log.warn("Unsupported channel: {}", channel);
                    }
                } catch (Exception e) {
                    log.error("Failed to queue message for user {} on channel {}", userId, channel, e);
                }
            }
        }

        return messageIds;
    }
}