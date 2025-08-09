package com.orbvpn.api.domain.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_deliveries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "webhook_configuration_id", nullable = false)
    private WebhookConfiguration webhook;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(nullable = false)
    private String status;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_attempt")
    private LocalDateTime lastAttempt;

    @Column(name = "next_attempt")
    private LocalDateTime nextAttempt;

    @Column(name = "response_data", columnDefinition = "TEXT")
    private String responseData;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        retryCount = 0;
    }
}