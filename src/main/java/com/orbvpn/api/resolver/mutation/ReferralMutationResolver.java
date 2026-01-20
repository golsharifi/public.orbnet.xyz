package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.ReferralLevelView;
import com.orbvpn.api.domain.entity.ReferralLevel;
import com.orbvpn.api.repostitory.ReferralLevelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

/**
 * GraphQL mutation resolver for referral-related mutations.
 * Admin-only operations for configuring referral levels.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ReferralMutationResolver {

    private final ReferralLevelRepository referralLevelRepository;

    /**
     * Create or update a referral level configuration.
     */
    @Secured(ADMIN)
    @MutationMapping
    @Transactional
    public ReferralLevelView upsertReferralLevel(
            @Argument Integer level,
            @Argument BigDecimal commissionPercent,
            @Argument String name,
            @Argument String description,
            @Argument BigDecimal minimumTokens,
            @Argument BigDecimal maximumTokens,
            @Argument Boolean active) {

        log.info("Upserting referral level {} with commission {}%", level, commissionPercent);

        if (level == null || level < 1) {
            throw new IllegalArgumentException("Level must be a positive integer");
        }

        if (commissionPercent == null || commissionPercent.compareTo(BigDecimal.ZERO) < 0
                || commissionPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Commission percent must be between 0 and 100");
        }

        ReferralLevel referralLevel = referralLevelRepository.findByLevel(level)
                .orElse(ReferralLevel.builder().level(level).build());

        referralLevel.setCommissionPercent(commissionPercent);

        if (name != null) {
            referralLevel.setName(name);
        } else if (referralLevel.getName() == null) {
            referralLevel.setName("Level " + level);
        }

        if (description != null) {
            referralLevel.setDescription(description);
        }

        if (minimumTokens != null) {
            referralLevel.setMinimumTokens(minimumTokens);
        }

        if (maximumTokens != null) {
            referralLevel.setMaximumTokens(maximumTokens);
        }

        if (active != null) {
            referralLevel.setActive(active);
        }

        referralLevel = referralLevelRepository.save(referralLevel);
        log.info("Saved referral level {}: {}% commission", level, commissionPercent);

        return toLevelView(referralLevel);
    }

    /**
     * Delete a referral level configuration.
     */
    @Secured(ADMIN)
    @MutationMapping
    @Transactional
    public boolean deleteReferralLevel(@Argument Integer level) {
        log.info("Deleting referral level {}", level);

        return referralLevelRepository.findByLevel(level)
                .map(l -> {
                    referralLevelRepository.delete(l);
                    log.info("Deleted referral level {}", level);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Toggle a referral level's active status.
     */
    @Secured(ADMIN)
    @MutationMapping
    @Transactional
    public ReferralLevelView toggleReferralLevel(@Argument Integer level) {
        log.info("Toggling referral level {} active status", level);

        ReferralLevel referralLevel = referralLevelRepository.findByLevel(level)
                .orElseThrow(() -> new IllegalArgumentException("Referral level not found: " + level));

        referralLevel.setActive(!referralLevel.isActive());
        referralLevel = referralLevelRepository.save(referralLevel);

        log.info("Referral level {} is now {}", level, referralLevel.isActive() ? "active" : "inactive");
        return toLevelView(referralLevel);
    }

    /**
     * Initialize default referral levels.
     * Creates standard MLM levels if they don't exist.
     */
    @Secured(ADMIN)
    @MutationMapping
    @Transactional
    public boolean initializeDefaultReferralLevels() {
        log.info("Initializing default referral levels");

        // Only initialize if no levels exist
        if (referralLevelRepository.count() > 0) {
            log.info("Referral levels already exist, skipping initialization");
            return false;
        }

        // Create default 3-level MLM structure
        ReferralLevel level1 = ReferralLevel.builder()
                .level(1)
                .name("Direct Referral")
                .description("Commission from users you directly invite")
                .commissionPercent(new BigDecimal("10.00"))
                .minimumTokens(BigDecimal.ZERO)
                .active(true)
                .build();

        ReferralLevel level2 = ReferralLevel.builder()
                .level(2)
                .name("Second Level")
                .description("Commission from users invited by your referrals")
                .commissionPercent(new BigDecimal("5.00"))
                .minimumTokens(BigDecimal.ZERO)
                .active(true)
                .build();

        ReferralLevel level3 = ReferralLevel.builder()
                .level(3)
                .name("Third Level")
                .description("Commission from third-level referrals")
                .commissionPercent(new BigDecimal("2.00"))
                .minimumTokens(BigDecimal.ZERO)
                .active(true)
                .build();

        referralLevelRepository.save(level1);
        referralLevelRepository.save(level2);
        referralLevelRepository.save(level3);

        log.info("Initialized 3 default referral levels");
        return true;
    }

    private ReferralLevelView toLevelView(ReferralLevel level) {
        return ReferralLevelView.builder()
                .id(level.getId())
                .level(level.getLevel())
                .name(level.getName())
                .description(level.getDescription())
                .commissionPercent(level.getCommissionPercent())
                .minimumTokens(level.getMinimumTokens())
                .maximumTokens(level.getMaximumTokens())
                .active(level.isActive())
                .build();
    }
}
