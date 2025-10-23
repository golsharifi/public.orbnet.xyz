package com.orbvpn.api.service.notification;

import com.google.firebase.messaging.*;
import com.orbvpn.api.domain.dto.FcmNotificationDto;
import com.orbvpn.api.domain.dto.NotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class FCMService {

    private static final String TOKEN_EMPTY = "Can not send push notification to null or empty token!";
    private static final String ERROR_MESSAGE = "Could not send notification to {%s}. Error: %s";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final FirebaseMessaging firebaseMessaging;

    public FcmNotificationDto sendNotification(NotificationDto notificationDto, String token) {
        if (token == null || token.trim().isEmpty()) {
            log.error(TOKEN_EMPTY);
            return new FcmNotificationDto(TOKEN_EMPTY);
        }

        if (notificationDto == null) {
            log.error("NotificationDto is null");
            return new FcmNotificationDto("Notification data cannot be null");
        }

        try {
            return sendNotificationWithRetry(notificationDto, token, 0);
        } catch (Exception e) {
            log.error("Final failure sending notification to token: {}", token, e);
            return new FcmNotificationDto(String.format(ERROR_MESSAGE, token, e.getMessage()));
        }
    }

    private FcmNotificationDto sendNotificationWithRetry(NotificationDto notificationDto, String token, int attempt)
            throws InterruptedException {
        try {
            Message message = buildMessage(notificationDto, token);
            String response = firebaseMessaging.sendAsync(message).get();
            log.info("Successfully sent message: {}", response);
            return new FcmNotificationDto();

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof FirebaseMessagingException) {
                FirebaseMessagingException fbError = (FirebaseMessagingException) cause;
                if (isRetryableError(fbError) && attempt < MAX_RETRY_ATTEMPTS) {
                    log.warn("Retryable error occurred (attempt {}): {}", attempt + 1, fbError.getMessage());
                    Thread.sleep(RETRY_DELAY_MS * (attempt + 1));
                    return sendNotificationWithRetry(notificationDto, token, attempt + 1);
                }
            }
            throw new RuntimeException("Failed to send FCM notification", e);
        }
    }

    private boolean isRetryableError(FirebaseMessagingException e) {
        MessagingErrorCode errorCode = e.getMessagingErrorCode();
        return errorCode == MessagingErrorCode.UNAVAILABLE
                || errorCode == MessagingErrorCode.INTERNAL
                || errorCode == MessagingErrorCode.THIRD_PARTY_AUTH_ERROR
                || errorCode == MessagingErrorCode.QUOTA_EXCEEDED;
    }

    private Message buildMessage(NotificationDto notificationDto, String token) {
        Map<String, String> data = notificationDto.getData();
        if (data == null) {
            data = new HashMap<>();
        }
        data.put("title", notificationDto.getSubject());
        data.put("body", notificationDto.getContent());
        data.put("click_action", "FLUTTER_NOTIFICATION_CLICK");

        return Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(notificationDto.getSubject())
                        .setBody(notificationDto.getContent())
                        .build())
                .putAllData(data)
                .setAndroidConfig(getAndroidConfig())
                .setApnsConfig(getApnsConfig(notificationDto))
                .build();
    }

    private AndroidConfig getAndroidConfig() {
        return AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setDirectBootOk(true)
                .setNotification(AndroidNotification.builder()
                        .setSound("default")
                        .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                        .setPriority(AndroidNotification.Priority.MAX)
                        .setVisibility(AndroidNotification.Visibility.PUBLIC)
                        .build())
                .build();
    }

    private ApnsConfig getApnsConfig(NotificationDto notificationDto) {
        return ApnsConfig.builder()
                .setAps(Aps.builder()
                        .setAlert(ApsAlert.builder()
                                .setTitle(notificationDto.getSubject()) // Use actual title
                                .setBody(notificationDto.getContent()) // Use actual body
                                .build())
                        .setSound("default")
                        .setContentAvailable(true)
                        .setMutableContent(true)
                        .setBadge(1)
                        .setThreadId("orbvpn_notifications")
                        .setCategory("message")
                        .build())
                .putHeader("apns-priority", "10")
                .putHeader("apns-push-type", "alert")
                .putHeader("apns-topic", "com.orbvpn.orbnet") // Replace with your iOS bundle ID
                .build();
    }

    public void sendLogoutNotification(String fcmToken) {
        if (fcmToken == null || fcmToken.trim().isEmpty()) {
            log.error("Cannot send logout notification to null or empty token!");
            return;
        }

        Map<String, String> data = new HashMap<>();
        data.put("action", "exit");
        data.put("title", "Logout");
        data.put("body", "Your device has been logged out.");

        NotificationDto notificationDto = NotificationDto.builder()
                .subject("Logout")
                .content("Your device has been logged out.")
                .data(data)
                .build();

        FcmNotificationDto result = sendNotification(notificationDto, fcmToken);
        if (!result.getStatus()) {
            log.error("Failed to send logout notification: {}", result.getMessage());
        }
    }

    public FcmNotificationDto sendBulkNotification(NotificationDto notificationDto, List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return new FcmNotificationDto("No valid tokens provided");
        }

        int notificationCount = 0;
        for (String token : tokens) {
            try {
                if (sendNotification(notificationDto, token).getStatus()) {
                    notificationCount++;
                }
            } catch (Exception e) {
                log.error("Failed to send notification to token: {}", token, e);
            }
        }

        return new FcmNotificationDto(notificationCount);
    }
}