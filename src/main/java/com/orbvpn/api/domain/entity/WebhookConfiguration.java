package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.entity.converter.LocaleConverter;
import com.orbvpn.api.domain.enums.WebhookProviderType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.Locale;

@Entity
@Table(name = "webhook_configurations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private WebhookProviderType providerType;

    @Column(nullable = false)
    private String endpoint;

    private String secret;

    @Default
    private boolean active = true;

    @ElementCollection
    @CollectionTable(name = "webhook_subscribed_events", joinColumns = @JoinColumn(name = "webhook_configuration_id"))
    @Column(name = "subscribed_events")
    private Set<String> subscribedEvents;

    @Column(name = "provider_specific_config", columnDefinition = "json")
    private String providerSpecificConfig;

    @Default
    @Column(name = "max_retries")
    private int maxRetries = 3;

    @Default
    @Column(name = "retry_delay")
    private int retryDelay = 60;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column
    @Convert(converter = LocaleConverter.class)
    private Locale defaultLanguage;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}