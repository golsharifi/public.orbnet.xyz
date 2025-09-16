package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "token_staking_tier_requirement")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenStakingTierRequirement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String requirementType;

    @Column(nullable = false)
    private String requirementValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staking_config_id", nullable = false)
    private TokenStakingConfig stakingConfig;
}