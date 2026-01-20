package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity to track WhatsApp messages sent through the system.
 * Provides delivery tracking and audit trail.
 */
@Entity
@Table(name = "whatsapp_message", indexes = {
        @Index(name = "idx_whatsapp_msg_phone", columnList = "phone_number"),
        @Index(name = "idx_whatsapp_msg_status", columnList = "status"),
        @Index(name = "idx_whatsapp_msg_created", columnList = "created_at"),
        @Index(name = "idx_whatsapp_msg_user", columnList = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MessageStatus status = MessageStatus.PENDING;

    @Column(name = "message_id", length = 100)
    private String whatsappMessageId;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "message_type", length = 50)
    @Builder.Default
    private String messageType = "TEXT";

    public enum MessageStatus {
        PENDING,
        SENDING,
        SENT,
        DELIVERED,
        FAILED,
        RATE_LIMITED
    }

    public void markAsSending() {
        this.status = MessageStatus.SENDING;
    }

    public void markAsSent(String messageId) {
        this.status = MessageStatus.SENT;
        this.whatsappMessageId = messageId;
        this.sentAt = LocalDateTime.now();
    }

    public void markAsDelivered() {
        this.status = MessageStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }

    public void markAsFailed(String error) {
        this.status = MessageStatus.FAILED;
        this.errorMessage = error;
        this.failedAt = LocalDateTime.now();
    }

    public void markAsRateLimited() {
        this.status = MessageStatus.RATE_LIMITED;
        this.errorMessage = "Rate limit exceeded";
        this.failedAt = LocalDateTime.now();
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }
}
