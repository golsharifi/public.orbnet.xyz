package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity for tracking processed Google Play notifications.
 * Used to implement idempotency and prevent duplicate processing.
 */
@Entity
@Table(name = "processed_google_notification", indexes = {
        @Index(name = "idx_processed_google_notification_message_id", columnList = "message_id", unique = true),
        @Index(name = "idx_processed_google_notification_processed_at", columnList = "processed_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ProcessedGoogleNotification {

    @Id
    @Column(name = "message_id", length = 128)
    private String messageId;

    @Column(name = "notification_type")
    private Integer notificationType;

    @Column(name = "purchase_token", length = 500)
    private String purchaseToken;

    @Column(name = "subscription_id", length = 128)
    private String subscriptionId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    public ProcessedGoogleNotification(String messageId) {
        this.messageId = messageId;
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
