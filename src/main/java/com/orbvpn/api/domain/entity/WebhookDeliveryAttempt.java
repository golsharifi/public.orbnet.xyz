package com.orbvpn.api.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_delivery_attempts")
@Data
@EqualsAndHashCode(of = "id")
public class WebhookDeliveryAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "delivery_id")
    private WebhookDelivery delivery;

    private LocalDateTime attemptTime;
    private String responseStatus;
    private String responseBody;
    private String errorMessage;
    private Long responseTimeMs;
    private Integer statusCode;

    @PrePersist
    protected void onCreate() {
        attemptTime = LocalDateTime.now();
    }
}