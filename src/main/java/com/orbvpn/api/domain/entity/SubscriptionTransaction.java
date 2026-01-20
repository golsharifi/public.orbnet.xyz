package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_transactions", indexes = {
                @Index(name = "idx_transaction_id", columnList = "transaction_id"),
                @Index(name = "idx_processed_at", columnList = "processed_at")
}, uniqueConstraints = {
                @UniqueConstraint(columnNames = { "transaction_id", "type" })
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionTransaction {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "transaction_id", nullable = false, length = 512)
        private String transactionId;

        @Column(nullable = false)
        private String subscriptionId;

        @Column(nullable = false)
        private String type;

        @Column(nullable = false)
        private LocalDateTime processedAt;

        @Version
        private Long version;
}