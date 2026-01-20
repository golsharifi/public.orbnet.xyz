package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity for tracking processed Apple App Store notifications.
 * Used to implement idempotency and prevent duplicate processing.
 */
@Entity
@Table(name = "processed_apple_notification", indexes = {
        @Index(name = "idx_processed_apple_notification_uuid", columnList = "notification_uuid", unique = true),
        @Index(name = "idx_processed_apple_notification_processed_at", columnList = "processed_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ProcessedAppleNotification {

    @Id
    @Column(name = "notification_uuid", length = 64)
    private String notificationUUID;

    @Column(name = "notification_type", length = 64)
    private String notificationType;

    @Column(name = "original_transaction_id", length = 64)
    private String originalTransactionId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    public ProcessedAppleNotification(String notificationUUID) {
        this.notificationUUID = notificationUUID;
        this.processedAt = LocalDateTime.now();
        this.status = "PROCESSING";
    }

    public void markSuccess() {
        this.status = "SUCCESS";
    }

    public void markFailed(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 500)
                : errorMessage;
    }
}
