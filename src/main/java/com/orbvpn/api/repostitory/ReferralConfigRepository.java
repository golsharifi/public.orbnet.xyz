package com.orbvpn.api.repostitory;

import com.orbvpn.api.domain.entity.ReferralConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for referral system configuration.
 */
@Repository
public interface ReferralConfigRepository extends JpaRepository<ReferralConfig, Long> {

    /**
     * Get the single configuration row.
     */
    default ReferralConfig getConfig() {
        return findById(1L).orElseGet(() -> {
            ReferralConfig config = ReferralConfig.builder().build();
            return save(config);
        });
    }
}
