package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.entity.UserProfile;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.UserProfileRepository;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.notification.TelegramService;
import com.orbvpn.api.service.notification.WhatsAppService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class NotificationResolver {

    private final UserProfileRepository userProfileRepository;
    private final WhatsAppService whatsAppService;
    private final TelegramService telegramService;
    private final UserService userService;

    @Secured(ADMIN)
    @MutationMapping
    public Boolean updateTelegramDetails(
            @Argument Integer userId,
            @Argument String username,
            @Argument String telegramUsername,
            @Argument String telegramChatId) {
        log.info("Updating Telegram details for user: {}", userId != null ? userId : username);
        try {
            if (userId != null) {
                userProfileRepository.updateTelegramUsernameByUserId(userId, telegramUsername);
                userProfileRepository.updateTelegramChatIdByUserId(userId, telegramChatId);
            } else if (username != null) {
                userProfileRepository.updateTelegramUsernameByUsername(username, telegramUsername);
                userProfileRepository.updateTelegramChatIdByUsername(username, telegramChatId);
            } else {
                throw new IllegalArgumentException("Either userId or username must be provided");
            }
            return true;
        } catch (Exception e) {
            log.error("Error updating Telegram details - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean sendWhatsAppNotification(
            @Argument Integer userId,
            @Argument String username,
            @Argument @Valid @NotBlank(message = "Message is required") String message) {
        log.info("Sending WhatsApp notification to user: {}", userId != null ? userId : username);
        try {
            UserProfile userProfile = resolveUserProfile(userId, username);
            if (userProfile != null && userProfile.getPhone() != null) {
                whatsAppService.sendMessage(userProfile.getPhone(), message);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Error sending WhatsApp notification - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean sendTelegramNotification(
            @Argument Integer userId,
            @Argument String username,
            @Argument @Valid @NotBlank(message = "Message is required") String message) {
        log.info("Sending Telegram notification to user: {}", userId != null ? userId : username);
        try {
            UserProfile userProfile = resolveUserProfile(userId, username);
            if (userProfile != null && userProfile.getTelegramChatId() != null) {
                telegramService.sendMessage(userProfile.getTelegramChatId(), message);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Error sending Telegram notification - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    private UserProfile resolveUserProfile(Integer userId, String username) {
        if (userId != null) {
            User user = userService.getUserById(userId);
            return userProfileRepository.findByUser(user)
                    .orElseThrow(() -> new IllegalArgumentException("User profile not found for ID: " + userId));
        } else if (username != null) {
            User user = userService.getUserByUsername(username);
            return userProfileRepository.findByUser(user)
                    .orElseThrow(
                            () -> new IllegalArgumentException("User profile not found for username: " + username));
        }
        throw new IllegalArgumentException("Either userId or username must be provided");
    }
}