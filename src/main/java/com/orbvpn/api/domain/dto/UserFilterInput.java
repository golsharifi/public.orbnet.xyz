package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.SocialMedia;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserFilterInput {
    // Subscription status filters (multi-select)
    private List<SubscriptionStatusFilter> subscriptionStatuses;

    // Account status
    private Boolean accountEnabled;

    // Role filters (multi-select)
    private List<String> roles;

    // Has devices
    private Boolean hasDevices;

    // Has token balance
    private Boolean hasTokenBalance;

    // Has referrals
    private Boolean hasReferrals;

    // Has open tickets
    private Boolean hasOpenTickets;

    // Contact info filters
    private Boolean hasTelegram;
    private Boolean hasPhone;

    // Location filters (multi-select)
    private List<String> countries;

    // Service group/plan filters (multi-select)
    private List<Integer> groupIds;
    private List<Integer> serviceGroupIds;

    // Reseller filter
    private Integer resellerId;

    // Date range filters
    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;
    private LocalDateTime expiresAfter;
    private LocalDateTime expiresBefore;

    // Payment gateway filters (multi-select)
    private List<GatewayName> paymentGateways;

    // OAuth provider filters (multi-select)
    private List<SocialMedia> oauthProviders;

    // Has passkeys filter
    private Boolean hasPasskeys;

    // Has OAuth filter
    private Boolean hasOauth;

    public enum SubscriptionStatusFilter {
        ACTIVE,
        EXPIRED,
        EXPIRING_SOON,
        NO_SUBSCRIPTION,
        TRIAL
    }
}
