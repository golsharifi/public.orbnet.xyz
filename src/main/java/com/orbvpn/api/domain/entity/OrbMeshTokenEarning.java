package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.EarningStatus;
import com.orbvpn.api.domain.enums.EarningType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OrbMesh Token Earning Entity
 * Tracks token earnings for partners and home device users.
 */
@Entity
@Table(name = "orbmesh_token_earning", indexes = {
    @Index(name = "idx_ote_node", columnList = "node_id"),
    @Index(name = "idx_ote_partner", columnList = "partner_id"),
    @Index(name = "idx_ote_user", columnList = "user_id"),
    @Index(name = "idx_ote_status", columnList = "status"),
    @Index(name = "idx_ote_period", columnList = "period_start, period_end")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrbMeshTokenEarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private OrbMeshNode node;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private OrbMeshPartner partner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "earning_type", nullable = false)
    private EarningType earningType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 8)
    private BigDecimal amount;

    @Column(name = "bandwidth_gb", precision = 10, scale = 4)
    private BigDecimal bandwidthGb;

    @Column(name = "compute_units", precision = 10, scale = 4)
    private BigDecimal computeUnits;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private EarningStatus status = EarningStatus.PENDING;

    @Column(name = "payout_tx_hash", length = 100)
    private String payoutTxHash;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    // Timestamps
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
