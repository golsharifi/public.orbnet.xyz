package com.orbvpn.api.repostitory;

import com.orbvpn.api.domain.entity.ReferralLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for MLM referral level configurations.
 */
@Repository
public interface ReferralLevelRepository extends JpaRepository<ReferralLevel, Long> {

    /**
     * Find a referral level by its level number.
     */
    Optional<ReferralLevel> findByLevel(int level);

    /**
     * Find all active referral levels ordered by level number.
     */
    List<ReferralLevel> findByActiveTrueOrderByLevelAsc();

    /**
     * Find active referral level by level number.
     */
    Optional<ReferralLevel> findByLevelAndActiveTrue(int level);

    /**
     * Get the maximum active level number.
     */
    @Query("SELECT MAX(r.level) FROM ReferralLevel r WHERE r.active = true")
    Integer findMaxActiveLevel();

    /**
     * Check if a level configuration exists.
     */
    boolean existsByLevel(int level);
}
