package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.UserProfileView;
import com.orbvpn.api.domain.entity.UserProfile;
import com.orbvpn.api.repository.UserProfileRepository;
import com.orbvpn.api.service.notification.TelegramService;
import com.orbvpn.api.mapper.UserProfileViewMapper;
import com.orbvpn.api.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserProfileService {
    private final UserProfileRepository userProfileRepository;
    private final UserProfileViewMapper userProfileViewMapper;
    private final TelegramService telegramService;

    @Transactional
    public void updateTelegramDetailsByUserId(int userId, String telegramUsername, String telegramChatId) {
        log.info("Updating Telegram details for user ID: {}", userId);

        // Validate Telegram details
        if (telegramUsername != null && !isValidTelegramUsername(telegramUsername)) {
            throw new BadRequestException("Invalid Telegram username format");
        }

        if (telegramChatId != null && !isValidTelegramChatId(telegramChatId)) {
            throw new BadRequestException("Invalid Telegram chat ID format");
        }

        // Verify connection if chat ID is provided
        if (telegramChatId != null) {
            verifyTelegramConnection(telegramChatId);
        }

        if (telegramUsername != null) {
            userProfileRepository.updateTelegramUsernameByUserId(userId, telegramUsername);
        }
        if (telegramChatId != null) {
            userProfileRepository.updateTelegramChatIdByUserId(userId, telegramChatId);
        }

        log.info("Successfully updated Telegram details for user ID: {}", userId);
    }

    @Transactional
    public void updateTelegramDetailsByUsername(String username, String telegramUsername, String telegramChatId) {
        log.info("Updating Telegram details for username: {}", username);

        // Validate Telegram details
        if (telegramUsername != null && !isValidTelegramUsername(telegramUsername)) {
            throw new BadRequestException("Invalid Telegram username format");
        }

        if (telegramChatId != null && !isValidTelegramChatId(telegramChatId)) {
            throw new BadRequestException("Invalid Telegram chat ID format");
        }

        // Verify connection if chat ID is provided
        if (telegramChatId != null) {
            verifyTelegramConnection(telegramChatId);
        }

        if (telegramUsername != null) {
            userProfileRepository.updateTelegramUsernameByUsername(username, telegramUsername);
        }
        if (telegramChatId != null) {
            userProfileRepository.updateTelegramChatIdByUsername(username, telegramChatId);
        }

        log.info("Successfully updated Telegram details for username: {}", username);
    }

    @Transactional
    public UserProfileView updateTelegramInfo(String username, String chatId) {
        log.info("Updating Telegram info for username: {}", username);

        if (!isValidTelegramChatId(chatId)) {
            throw new BadRequestException("Invalid Telegram chat ID format");
        }

        // Verify connection
        verifyTelegramConnection(chatId);

        userProfileRepository.updateTelegramChatIdByUsername(username, chatId);

        UserProfile profile = userProfileRepository.findByUser_Username(username)
                .orElseThrow(() -> new BadRequestException("User profile not found"));

        return userProfileViewMapper.toView(profile);
    }

    private boolean isValidTelegramUsername(String username) {
        // Telegram usernames must:
        // - Be 5-32 characters long
        // - Start with a letter
        // - Consist of letters, numbers, and underscores
        return username.matches("^[a-zA-Z][a-zA-Z0-9_]{4,31}$");
    }

    private boolean isValidTelegramChatId(String chatId) {
        // Telegram chat IDs are numerical values
        return chatId.matches("^-?\\d+$");
    }

    private void verifyTelegramConnection(String chatId) {
        try {
            if (!telegramService.testConnection(chatId)) {
                throw new BadRequestException("Could not verify Telegram connection");
            }
        } catch (Exception e) {
            throw new BadRequestException("Failed to verify Telegram connection: " + e.getMessage());
        }
    }
}