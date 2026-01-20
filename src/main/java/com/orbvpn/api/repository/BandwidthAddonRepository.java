package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.BandwidthAddon;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BandwidthAddonRepository extends JpaRepository<BandwidthAddon, Long> {

    /**
     * Find addon by purchase token (for preventing duplicates)
     */
    Optional<BandwidthAddon> findByPurchaseToken(String purchaseToken);

    /**
     * Find addon by order ID
     */
    Optional<BandwidthAddon> findByOrderId(String orderId);

    /**
     * Find all addons for a user
     */
    List<BandwidthAddon> findByUserOrderByCreatedAtDesc(User user);

    /**
     * Find all addons for a subscription
     */
    List<BandwidthAddon> findBySubscriptionOrderByCreatedAtDesc(UserSubscription subscription);

    /**
     * Find unapplied addons for a subscription
     */
    List<BandwidthAddon> findBySubscriptionAndAppliedFalse(UserSubscription subscription);

    /**
     * Sum total bandwidth purchased by a user
     */
    @Query("SELECT COALESCE(SUM(ba.bandwidthBytes), 0) " +
            "FROM BandwidthAddon ba " +
            "WHERE ba.user.id = :userId " +
            "AND ba.applied = true")
    Long sumAppliedBandwidthByUserId(@Param("userId") Integer userId);

    /**
     * Sum total bandwidth purchased for a subscription
     */
    @Query("SELECT COALESCE(SUM(ba.bandwidthBytes), 0) " +
            "FROM BandwidthAddon ba " +
            "WHERE ba.subscription.id = :subscriptionId " +
            "AND ba.applied = true")
    Long sumAppliedBandwidthBySubscriptionId(@Param("subscriptionId") Integer subscriptionId);

    /**
     * Check if purchase token already exists
     */
    boolean existsByPurchaseToken(String purchaseToken);

    /**
     * Count addons for a user
     */
    long countByUser(User user);

    /**
     * Find all addons paginated (admin)
     */
    Page<BandwidthAddon> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Sum total revenue from addon sales
     */
    @Query("SELECT COALESCE(SUM(ba.price), 0) FROM BandwidthAddon ba WHERE ba.isPromotional = false")
    BigDecimal sumTotalRevenue();

    /**
     * Count promotional addons
     */
    long countByIsPromotionalTrue();

    /**
     * Find addons by user ID
     */
    List<BandwidthAddon> findByUserIdOrderByCreatedAtDesc(Integer userId);

    /**
     * Delete all addons for a user
     */
    void deleteByUser(User user);
}
