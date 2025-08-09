package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mining_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiningSettingsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @PositiveOrZero
    @Column(precision = 19, scale = 8)
    private BigDecimal minWithdrawAmount;

    @Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid Ethereum address format")
    @Column(length = 42)
    private String withdrawAddress;

    @NotNull
    @Builder.Default
    @Column(nullable = false)
    private Boolean autoWithdraw = false;

    @NotNull
    @Builder.Default
    @Column(name = "notifications_enabled", nullable = false)
    private Boolean notificationsEnabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (autoWithdraw == null) {
            autoWithdraw = false;
        }
        if (notificationsEnabled == null) {
            notificationsEnabled = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Utility methods
    public boolean isWithdrawalConfigured() {
        return withdrawAddress != null &&
                minWithdrawAmount != null &&
                minWithdrawAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean canAutoWithdraw(BigDecimal balance) {
        return autoWithdraw &&
                isWithdrawalConfigured() &&
                balance.compareTo(minWithdrawAmount) >= 0;
    }

    public void enableAutoWithdraw() {
        if (!isWithdrawalConfigured()) {
            throw new IllegalStateException("Withdrawal configuration is incomplete");
        }
        this.autoWithdraw = true;
    }

    public void disableAutoWithdraw() {
        this.autoWithdraw = false;
    }

    public void toggleNotifications() {
        this.notificationsEnabled = !this.notificationsEnabled;
    }
}
