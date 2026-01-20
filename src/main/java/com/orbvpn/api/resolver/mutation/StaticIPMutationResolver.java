package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.staticip.*;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.*;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.staticip.PortForwardService;
import com.orbvpn.api.service.staticip.StaticIPIAPService;
import com.orbvpn.api.service.staticip.StaticIPPaymentService;
import com.orbvpn.api.service.staticip.StaticIPService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class StaticIPMutationResolver {

    private final StaticIPService staticIPService;
    private final StaticIPPaymentService staticIPPaymentService;
    private final StaticIPIAPService staticIPIAPService;
    private final PortForwardService portForwardService;
    private final UserService userService;

    // ========== SUBSCRIPTION MUTATIONS ==========

    @Secured(USER)
    @MutationMapping
    public StaticIPSubscriptionResponse createStaticIPSubscription(
            @Argument CreateStaticIPSubscriptionInput input) {
        log.info("Creating static IP subscription with plan: {}, paymentMethod: {}",
                input.getPlanType(), input.getPaymentMethod());
        try {
            User user = userService.getUser();

            // Process payment and create subscription
            return staticIPPaymentService.processSubscriptionWithPayment(
                    user,
                    input.getPlanType(),
                    input.getPaymentMethod(),
                    input.getSelectedCoin(),
                    input.isAutoRenew()
            );
        } catch (Exception e) {
            log.error("Error creating static IP subscription: {}", e.getMessage(), e);
            return StaticIPSubscriptionResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build();
        }
    }

    // ========== IN-APP PURCHASE VERIFICATION ==========

    /**
     * Verify Apple In-App Purchase and create Static IP subscription.
     * Called by Flutter after completing StoreKit purchase.
     *
     * @param receipt              Base64 encoded Apple receipt
     * @param productId            Product ID purchased (e.g., "ios_staticip_personal")
     * @param transactionId        Transaction ID from Apple
     * @param originalTransactionId Original transaction ID for subscription
     */
    @Secured(USER)
    @MutationMapping
    public StaticIPSubscriptionResponse verifyStaticIPApplePurchase(
            @Argument String receipt,
            @Argument String productId,
            @Argument String transactionId,
            @Argument String originalTransactionId) {
        log.info("Verifying Apple purchase for Static IP: product={}, txn={}", productId, transactionId);
        return staticIPIAPService.processApplePurchase(receipt, productId, transactionId, originalTransactionId);
    }

    /**
     * Verify Google Play In-App Purchase and create Static IP subscription.
     * Called by Flutter after completing Google Play Billing purchase.
     *
     * @param purchaseToken Purchase token from Google Play
     * @param productId     Product ID purchased (e.g., "android_staticip_personal")
     * @param orderId       Order ID from Google Play
     */
    @Secured(USER)
    @MutationMapping
    public StaticIPSubscriptionResponse verifyStaticIPGooglePlayPurchase(
            @Argument String purchaseToken,
            @Argument String productId,
            @Argument String orderId) {
        log.info("Verifying Google Play purchase for Static IP: product={}, order={}", productId, orderId);
        return staticIPIAPService.processGooglePlayPurchase(purchaseToken, productId, orderId);
    }

    @Secured(USER)
    @MutationMapping
    public Boolean cancelStaticIPSubscription() {
        log.info("Cancelling static IP subscription for current user");
        try {
            User user = userService.getUser();
            staticIPService.cancelSubscription(user);
            return true;
        } catch (Exception e) {
            log.error("Error cancelling static IP subscription: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @MutationMapping
    public StaticIPSubscriptionResponse changeStaticIPPlan(
            @Argument StaticIPPlanType newPlanType) {
        log.info("Changing static IP plan to: {}", newPlanType);
        try {
            User user = userService.getUser();
            StaticIPSubscription subscription = staticIPService.changePlan(user, newPlanType);

            return StaticIPSubscriptionResponse.builder()
                    .success(true)
                    .message("Plan changed successfully")
                    .subscription(subscription)
                    .build();
        } catch (Exception e) {
            log.error("Error changing static IP plan: {}", e.getMessage(), e);
            return StaticIPSubscriptionResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build();
        }
    }

    // ========== ALLOCATION MUTATIONS ==========

    @Secured(USER)
    @MutationMapping
    public StaticIPAllocationResponse allocateStaticIP(
            @Argument AllocateStaticIPInput input) {
        log.info("Allocating static IP in region: {}", input.getRegion());
        try {
            User user = userService.getUser();
            StaticIPAllocation allocation = staticIPService.allocateStaticIP(user, input.getRegion());

            return StaticIPAllocationResponse.builder()
                    .success(true)
                    .message("Static IP allocated successfully. IP: " + allocation.getPublicIp())
                    .allocation(allocation)
                    .build();
        } catch (Exception e) {
            log.error("Error allocating static IP: {}", e.getMessage(), e);
            return StaticIPAllocationResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build();
        }
    }

    @Secured(USER)
    @MutationMapping
    public Boolean releaseStaticIP(@Argument Long allocationId) {
        log.info("Releasing static IP allocation: {}", allocationId);
        try {
            User user = userService.getUser();
            staticIPService.releaseStaticIP(user, allocationId);
            return true;
        } catch (Exception e) {
            log.error("Error releasing static IP: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ========== PORT FORWARDING MUTATIONS ==========

    @Secured(USER)
    @MutationMapping
    public PortForwardResponse createPortForward(
            @Argument CreatePortForwardInput input) {
        log.info("Creating port forward rule: {}:{} -> {}",
                input.getAllocationId(), input.getExternalPort(), input.getInternalPort());
        try {
            User user = userService.getUser();

            CreatePortForwardRequest request = CreatePortForwardRequest.builder()
                    .allocationId(input.getAllocationId())
                    .externalPort(input.getExternalPort())
                    .internalPort(input.getInternalPort())
                    .protocol(input.getProtocol())
                    .description(input.getDescription())
                    .build();

            PortForwardRule rule = portForwardService.createPortForwardRule(user, request);

            return PortForwardResponse.builder()
                    .success(true)
                    .message("Port forward rule created successfully")
                    .rule(rule)
                    .build();
        } catch (Exception e) {
            log.error("Error creating port forward rule: {}", e.getMessage(), e);
            return PortForwardResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build();
        }
    }

    @Secured(USER)
    @MutationMapping
    public Boolean deletePortForward(@Argument Long ruleId) {
        log.info("Deleting port forward rule: {}", ruleId);
        try {
            User user = userService.getUser();
            portForwardService.deletePortForwardRule(user, ruleId);
            return true;
        } catch (Exception e) {
            log.error("Error deleting port forward rule: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @MutationMapping
    public PortForwardResponse togglePortForward(
            @Argument Long ruleId,
            @Argument Boolean enabled) {
        log.info("Toggling port forward rule {} to {}", ruleId, enabled);
        try {
            User user = userService.getUser();
            PortForwardRule rule = portForwardService.togglePortForwardRule(user, ruleId, enabled);

            return PortForwardResponse.builder()
                    .success(true)
                    .message("Port forward rule " + (enabled ? "enabled" : "disabled"))
                    .rule(rule)
                    .build();
        } catch (Exception e) {
            log.error("Error toggling port forward rule: {}", e.getMessage(), e);
            return PortForwardResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build();
        }
    }

    // ========== PORT FORWARD ADDON MUTATIONS ==========

    @Secured(USER)
    @MutationMapping
    public PortForwardAddonResponse purchasePortForwardAddon(
            @Argument PurchasePortForwardAddonInput input) {
        log.info("Purchasing port forward addon: {} for allocation: {}",
                input.getAddonType(), input.getAllocationId());
        try {
            User user = userService.getUser();

            // TODO: Process payment first
            PortForwardAddon addon = portForwardService.purchaseAddon(
                    user,
                    input.getAllocationId(),
                    input.getAddonType(),
                    null  // externalSubscriptionId from payment
            );

            return PortForwardAddonResponse.builder()
                    .success(true)
                    .message("Port forward addon purchased successfully")
                    .addon(addon)
                    .build();
        } catch (Exception e) {
            log.error("Error purchasing port forward addon: {}", e.getMessage(), e);
            return PortForwardAddonResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build();
        }
    }

    @Secured(USER)
    @MutationMapping
    public Boolean cancelPortForwardAddon(@Argument Long addonId) {
        log.info("Cancelling port forward addon: {}", addonId);
        try {
            User user = userService.getUser();
            portForwardService.cancelAddon(user, addonId);
            return true;
        } catch (Exception e) {
            log.error("Error cancelling port forward addon: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ========== ADMIN MUTATIONS ==========

    @Secured(ADMIN)
    @MutationMapping
    public Integer adminAddStaticIPToPool(
            @Argument String region,
            @Argument java.util.List<String> publicIps) {
        log.info("Admin adding {} IPs to pool in region: {}", publicIps.size(), region);
        // TODO: Implement admin IP pool management
        return publicIps.size();
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean adminRemoveStaticIPFromPool(@Argument String publicIp) {
        log.info("Admin removing IP from pool: {}", publicIp);
        // TODO: Implement admin IP pool management
        return true;
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean adminCancelStaticIPSubscription(@Argument Integer userId) {
        log.info("Admin cancelling static IP subscription for user: {}", userId);
        try {
            User user = userService.getUserById(userId);
            staticIPService.cancelSubscription(user);
            return true;
        } catch (Exception e) {
            log.error("Error admin cancelling subscription: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean adminReleaseStaticIPAllocation(@Argument Long allocationId) {
        log.info("Admin releasing static IP allocation: {}", allocationId);
        // TODO: Implement admin release that bypasses ownership check
        return true;
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean adminUpdateStaticIPAllocationStatus(
            @Argument Long allocationId,
            @Argument StaticIPAllocationStatus status,
            @Argument String error) {
        log.info("Admin updating allocation {} status to: {}", allocationId, status);
        try {
            staticIPService.updateAllocationStatus(allocationId, status, error);
            return true;
        } catch (Exception e) {
            log.error("Error updating allocation status: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean adminUpdatePortForwardStatus(
            @Argument Long ruleId,
            @Argument PortForwardStatus status,
            @Argument String error) {
        log.info("Admin updating port forward rule {} status to: {}", ruleId, status);
        try {
            portForwardService.updateRuleStatus(ruleId, status, error);
            return true;
        } catch (Exception e) {
            log.error("Error updating port forward status: {}", e.getMessage(), e);
            throw e;
        }
    }
}
