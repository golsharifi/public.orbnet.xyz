package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "token_staking_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenStakingConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal baseApy;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal bonusApyPerMonth;

    @Column(nullable = false)
    private Integer minimumLockDays;

    @Column(nullable = false)
    private Integer maximumLockDays;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal minimumStakeAmount;

    @Column(precision = 19, scale = 4)
    private BigDecimal maximumStakeAmount;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "stakingConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TokenStakingTierRequirement> requirements = new ArrayList<>();

    public void addRequirement(TokenStakingTierRequirement requirement) {
        requirements.add(requirement);
        requirement.setStakingConfig(this);
    }

    public void removeRequirement(TokenStakingTierRequirement requirement) {
        requirements.remove(requirement);
        requirement.setStakingConfig(null);
    }
}