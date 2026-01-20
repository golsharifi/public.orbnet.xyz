package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.BandwidthAddon;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.exception.BadRequestException;
import com.orbvpn.api.exception.DuplicatePurchaseTokenException;
import com.orbvpn.api.repository.BandwidthAddonRepository;
import com.orbvpn.api.repository.UserSubscriptionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing bandwidth addon purchases.
 * Users can purchase additional bandwidth packages.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BandwidthAddonService {

    private final BandwidthAddonRepository addonRepository;
    private final UserSubscriptionRepository subscriptionRepository;

    // Bandwidth addon products (product ID -> bytes)
    private static final Map<String, Long> BANDWIDTH_PRODUCTS = new HashMap<>();
    static {
        // 1 GB = 1024 * 1024 * 1024 bytes
        long GB = 1024L * 1024L * 1024L;

        BANDWIDTH_PRODUCTS.put("bandwidth_1gb", 1 * GB);
        BANDWIDTH_PRODUCTS.put("bandwidth_5gb", 5 * GB);
        BANDWIDTH_PRODUCTS.put("bandwidth_10gb", 10 * GB);
        BANDWIDTH_PRODUCTS.put("bandwidth_25gb", 25 * GB);
        BANDWIDTH_PRODUCTS.put("bandwidth_50gb", 50 * GB);
        BANDWIDTH_PRODUCTS.put("bandwidth_100gb", 100 * GB);
        BANDWIDTH_PRODUCTS.put("bandwidth_unlimited_monthly", -1L); // Special: unlimited
    }

    /**
     * Process a bandwidth addon purchase from app store.
     */
    @Transactional
    public BandwidthAddon processPurchase(User user, String productId, String purchaseToken,
                                           String orderId, GatewayName gateway,
                                           BigDecimal price, String currency) {
        // Check for duplicate purchase token
        if (addonRepository.existsByPurchaseToken(purchaseToken)) {
            log.warn("Duplicate bandwidth addon purchase token: {}", purchaseToken);
            throw new DuplicatePurchaseTokenException("This purchase has already been processed");
        }

        // Validate product ID
        Long bandwidthBytes = BANDWIDTH_PRODUCTS.get(productId);
        if (bandwidthBytes == null) {
            log.error("Invalid bandwidth addon product ID: {}", productId);
            throw new BadRequestException("Invalid bandwidth product: " + productId);
        }

        // Get active subscription
        UserSubscription subscription = user.getCurrentSubscription();
        if (subscription == null) {
            throw new BadRequestException("No active subscription to add bandwidth to");
        }

        // Create addon record
        BandwidthAddon addon = BandwidthAddon.builder()
                .user(user)
                .subscription(subscription)
                .productId(productId)
                .bandwidthBytes(bandwidthBytes)
                .price(price)
                .currency(currency)
                .gateway(gateway)
                .purchaseToken(purchaseToken)
                .orderId(orderId)
                .applied(false)
                .build();

        addon = addonRepository.save(addon);

        // Apply the addon to subscription
        applyAddon(addon);

        log.info("Processed bandwidth addon purchase: user={}, product={}, bytes={}",
                user.getEmail(), productId, bandwidthBytes);

        return addon;
    }

    /**
     * Apply a bandwidth addon to the subscription.
     */
    @Transactional
    public void applyAddon(BandwidthAddon addon) {
        if (addon.getApplied()) {
            log.warn("Bandwidth addon {} already applied", addon.getId());
            return;
        }

        UserSubscription subscription = addon.getSubscription();

        // Handle unlimited addon
        if (addon.getBandwidthBytes() == -1L) {
            subscription.setBandwidthQuotaBytes(null); // null = unlimited
            log.info("Applied unlimited bandwidth addon to subscription {}", subscription.getId());
        } else {
            // Add bandwidth to subscription
            subscription.addBandwidthAddon(addon.getBandwidthBytes());
            log.info("Added {} bytes bandwidth to subscription {}",
                    addon.getBandwidthBytes(), subscription.getId());
        }

        subscriptionRepository.save(subscription);

        // Mark addon as applied
        addon.setApplied(true);
        addon.setAppliedAt(LocalDateTime.now());
        addonRepository.save(addon);
    }

    /**
     * Apply all unapplied addons for a subscription.
     */
    @Transactional
    public int applyPendingAddons(UserSubscription subscription) {
        List<BandwidthAddon> pendingAddons = addonRepository
                .findBySubscriptionAndAppliedFalse(subscription);

        for (BandwidthAddon addon : pendingAddons) {
            applyAddon(addon);
        }

        return pendingAddons.size();
    }

    /**
     * Grant promotional bandwidth addon (admin function).
     */
    @Transactional
    public BandwidthAddon grantPromotionalBandwidth(User user, long bandwidthBytes, String notes) {
        UserSubscription subscription = user.getCurrentSubscription();
        if (subscription == null) {
            throw new BadRequestException("User has no active subscription");
        }

        BandwidthAddon addon = BandwidthAddon.builder()
                .user(user)
                .subscription(subscription)
                .productId("promotional")
                .bandwidthBytes(bandwidthBytes)
                .isPromotional(true)
                .notes(notes)
                .applied(false)
                .build();

        addon = addonRepository.save(addon);
        applyAddon(addon);

        log.info("Granted {} bytes promotional bandwidth to user {}",
                bandwidthBytes, user.getEmail());

        return addon;
    }

    /**
     * Get all bandwidth addons for a user.
     */
    public List<BandwidthAddon> getUserAddons(User user) {
        return addonRepository.findByUserOrderByCreatedAtDesc(user);
    }

    /**
     * Get total purchased bandwidth for a user.
     */
    public long getTotalPurchasedBandwidth(Integer userId) {
        Long total = addonRepository.sumAppliedBandwidthByUserId(userId);
        return total != null ? total : 0L;
    }

    /**
     * Get available bandwidth products.
     */
    public Map<String, BandwidthProduct> getAvailableProducts() {
        Map<String, BandwidthProduct> products = new HashMap<>();

        products.put("bandwidth_1gb", new BandwidthProduct("bandwidth_1gb", "1 GB", 1, 0.99));
        products.put("bandwidth_5gb", new BandwidthProduct("bandwidth_5gb", "5 GB", 5, 3.99));
        products.put("bandwidth_10gb", new BandwidthProduct("bandwidth_10gb", "10 GB", 10, 6.99));
        products.put("bandwidth_25gb", new BandwidthProduct("bandwidth_25gb", "25 GB", 25, 14.99));
        products.put("bandwidth_50gb", new BandwidthProduct("bandwidth_50gb", "50 GB", 50, 24.99));
        products.put("bandwidth_100gb", new BandwidthProduct("bandwidth_100gb", "100 GB", 100, 39.99));

        return products;
    }

    /**
     * Get all bandwidth addons paginated (admin).
     */
    public org.springframework.data.domain.Page<BandwidthAddon> getAllAddonsPaginated(int page, int size) {
        return addonRepository.findAllByOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(page, size));
    }

    /**
     * Get total revenue from bandwidth addon sales.
     */
    public BigDecimal getTotalRevenue() {
        BigDecimal total = addonRepository.sumTotalRevenue();
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Get total number of addons purchased.
     */
    public long getTotalAddonCount() {
        return addonRepository.count();
    }

    /**
     * Bandwidth product DTO.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class BandwidthProduct {
        private String productId;
        private String name;
        private int gigabytes;
        private double priceUsd;
    }
}
