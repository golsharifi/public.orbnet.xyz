package com.orbvpn.api.domain.entity;

import com.orbvpn.api.exception.InsufficientBalanceException;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "token_balances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Column(precision = 19, scale = 8, nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "last_activity_date")
    private LocalDateTime lastActivityDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
        if (lastActivityDate == null) {
            lastActivityDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        lastActivityDate = LocalDateTime.now();
    }

    @Transient
    public Integer getUserId() {
        return user != null ? user.getId() : null;
    }

    public void addToBalance(BigDecimal amount) {
        if (amount != null) {
            this.balance = this.balance.add(amount);
        }
    }

    public void subtractFromBalance(BigDecimal amount) {
        if (amount != null && this.balance.compareTo(amount) >= 0) {
            this.balance = this.balance.subtract(amount);
        } else {
            throw new InsufficientBalanceException("Insufficient balance");
        }
    }

    public boolean hasEnoughBalance(BigDecimal amount) {
        return amount != null && this.balance.compareTo(amount) >= 0;
    }
}
