package com.orbvpn.api.service.notification;

import com.orbvpn.api.config.messaging.TelegramConfig;
import com.orbvpn.api.domain.dto.TelegramRegistration;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.*;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.exception.ChatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import java.time.LocalDateTime;

@Slf4j
@Service
public class TelegramService extends TelegramLongPollingBot {
    private final TelegramConfig config;
    private final UserProfileRepository userProfileRepository;
    private final TelegramMessageRepository messageRepository;
    private final UserTelegramInfoRepository telegramInfoRepository;

    public TelegramService(TelegramConfig config,
            UserProfileRepository userProfileRepository,
            TelegramMessageRepository messageRepository,
            UserTelegramInfoRepository telegramInfoRepository) {
        super(config.getBotToken());
        this.config = config;
        this.userProfileRepository = userProfileRepository;
        this.messageRepository = messageRepository;
        this.telegramInfoRepository = telegramInfoRepository;
    }

    @Override
    public String getBotUsername() {
        return config.getBotUsername();
    }

    public void registerUser(TelegramRegistration registration) {
        try {
            String username = registration.getUsername().toLowerCase().replace("@", "");

            userProfileRepository.findByTelegramUsername(username)
                    .ifPresentOrElse(profile -> {
                        UserTelegramInfo telegramInfo = telegramInfoRepository
                                .findByTelegramUsername(username)
                                .orElse(new UserTelegramInfo());

                        telegramInfo.setUser(profile.getUser());
                        telegramInfo.setTelegramUsername(username);
                        telegramInfoRepository.save(telegramInfo);

                        if (registration.getVerificationCode() != null && profile.getTelegramChatId() != null) {
                            try {
                                SendMessage verificationMessage = new SendMessage();
                                verificationMessage.setChatId(profile.getTelegramChatId());
                                verificationMessage.setText("Your verification code is: " +
                                        registration.getVerificationCode());
                                execute(verificationMessage);
                            } catch (Exception e) {
                                log.error("Failed to send verification message", e);
                                throw new ChatException("Failed to send verification message");
                            }
                        }
                    },
                            () -> {
                                throw new ChatException("No user found with the provided Telegram username");
                            });

            log.debug("Telegram user registered: {}", username);
        } catch (Exception e) {
            log.error("Failed to register Telegram user", e);
            throw new ChatException("Failed to register Telegram user: " + e.getMessage());
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String username = update.getMessage().getFrom().getUserName();
            String chatId = update.getMessage().getChatId().toString();
            String messageText = update.getMessage().getText();

            // Check if this is from admin group
            if (chatId.equals(config.getAdminGroupId())) {
                handleAdminMessage(username, messageText);
                return;
            }

            // Update telegram info if exists
            telegramInfoRepository.findByTelegramUsername(username)
                    .ifPresent(info -> {
                        info.setTelegramChatId(chatId);
                        telegramInfoRepository.save(info);
                    });

            // Handle user message
            if (messageText.startsWith("/start")) {
                handleStartCommand(username, chatId);
            } else {
                handleUserMessage(username, chatId, messageText);
            }
        }
    }

    private void handleStartCommand(String username, String chatId) {
        try {
            userProfileRepository.findByTelegramUsername(username).ifPresentOrElse(
                    profile -> {
                        profile.setTelegramChatId(chatId);
                        userProfileRepository.save(profile);
                        sendMessage(chatId, "Successfully connected to OrbVPN! You'll receive notifications here.");
                    },
                    () -> sendMessage(chatId, "Please register your Telegram username in OrbVPN first."));
        } catch (Exception e) {
            log.error("Error handling start command", e);
        }
    }

    private void handleUserMessage(String username, String chatId, String messageText) {
        try {
            userProfileRepository.findByTelegramUsername(username).ifPresentOrElse(
                    profile -> {
                        // Store the message
                        TelegramMessage message = TelegramMessage.builder()
                                .user(profile.getUser())
                                .message(messageText)
                                .timestamp(LocalDateTime.now())
                                .direction(MessageDirection.INCOMING)
                                .status(MessageStatus.RECEIVED)
                                .build();
                        messageRepository.save(message);

                        // Forward to admin group
                        String forwardMessage = String.format(
                                "New message from %s (%s):\n\n%s\n\nReply with: /reply %d <message>",
                                username,
                                profile.getUser().getEmail(),
                                messageText,
                                message.getId());
                        sendMessage(config.getAdminGroupId(), forwardMessage);

                        // Acknowledge to user
                        sendMessage(chatId, "Message received! Our support team will respond shortly.");
                    },
                    () -> sendMessage(chatId, "Please register your Telegram username in OrbVPN first."));
        } catch (Exception e) {
            log.error("Error handling user message", e);
        }
    }

    private void handleAdminMessage(String adminUsername, String messageText) {
        if (messageText.startsWith("/reply")) {
            try {
                String[] parts = messageText.split(" ", 3);
                if (parts.length < 3) {
                    sendMessage(config.getAdminGroupId(), "Usage: /reply <message_id> <message>");
                    return;
                }

                Long messageId = Long.parseLong(parts[1]);
                String replyText = parts[2];

                messageRepository.findById(messageId).ifPresent(originalMessage -> {
                    User user = originalMessage.getUser();
                    UserProfile profile = user.getProfile();

                    if (profile != null && profile.getTelegramChatId() != null) {
                        // Send reply to user
                        sendMessage(profile.getTelegramChatId(), replyText);

                        // Store admin's reply
                        TelegramMessage reply = TelegramMessage.builder()
                                .user(user)
                                .message(replyText)
                                .timestamp(LocalDateTime.now())
                                .direction(MessageDirection.OUTGOING)
                                .status(MessageStatus.SENT)
                                .adminUsername(adminUsername)
                                .build();
                        messageRepository.save(reply);

                        // Update original message status
                        originalMessage.setStatus(MessageStatus.REPLIED);
                        messageRepository.save(originalMessage);

                        sendMessage(config.getAdminGroupId(), "Reply sent successfully!");
                    }
                });
            } catch (NumberFormatException e) {
                sendMessage(config.getAdminGroupId(), "Invalid message ID format");
            } catch (Exception e) {
                log.error("Error handling admin reply", e);
                sendMessage(config.getAdminGroupId(), "Error sending reply: " + e.getMessage());
            }
        }
    }

    public void sendMessage(String chatId, String message) {
        try {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText(message);
            execute(sendMessage);
            log.debug("Telegram message sent to: {}", chatId);
        } catch (Exception e) {
            log.error("Failed to send Telegram message", e);
            throw new ChatException("Failed to send Telegram message");
        }
    }

    public boolean testConnection(String chatId) {
        try {
            sendMessage(chatId, "Connection test successful!");
            return true;
        } catch (Exception e) {
            log.error("Failed to test Telegram connection", e);
            return false;
        }
    }
}