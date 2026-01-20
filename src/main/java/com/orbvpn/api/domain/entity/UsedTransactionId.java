package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.GatewayName;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "used_transaction_id", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "transaction_id", "gateway" })
})
@Getter
@Setter
public class UsedTransactionId {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", length = 513)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private GatewayName gateway;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;

    // Constructors
    public UsedTransactionId() {
    }

    public UsedTransactionId(String transactionId, GatewayName gateway) {
        this.transactionId = transactionId;
        this.gateway = gateway;
        this.usedAt = LocalDateTime.now();
    }
}