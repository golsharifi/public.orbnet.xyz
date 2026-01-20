package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.domain.entity.TokenRate;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.GroupRepository;
import com.orbvpn.api.repository.TokenRateRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenSubscriptionService {
    private final UserSubscriptionRepository subscriptionRepository;
    private final RadiusService radiusService;
    private final TokenRateRepository tokenRateRepository;
    private final GroupRepository groupRepository;

    @Transactional
    public UserSubscription createTokenSubscription(User user) {
        // Cancel any existing subscriptions
        subscriptionRepository.deleteByUserId(user.getId());

        TokenRate defaultRate = tokenRateRepository.findByRegionAndAdVendor("DEFAULT", "DEFAULT")
                .orElseThrow(() -> new RuntimeException("No default token rate configured"));

        UserSubscription subscription = new UserSubscription();
        subscription.setUser(user);
        subscription.setGroup(getTokenBasedGroup());
        subscription.setGateway(GatewayName.TOKEN_BASED);
        subscription.setIsTokenBased(true);
        subscription.setWeeklyAdsWatched(0);
        subscription.setLastWeeklyReset(LocalDateTime.now());
        subscription.setMultiLoginCount(defaultRate.getDeviceLimit());
        subscription.setExpiresAt(LocalDateTime.now().plusWeeks(1));

        subscription = subscriptionRepository.save(subscription);
        radiusService.createUserRadChecks(subscription);

        return subscription;
    }

    private Group getTokenBasedGroup() {
        return groupRepository.findByName("TOKEN_BASED_GROUP")
                .orElseThrow(() -> new RuntimeException("Token-based group not found"));
    }

    @Scheduled(cron = "0 0 0 * * MON") // Run every Monday at midnight
    public void resetWeeklyStats() {
        List<UserSubscription> tokenSubscriptions = subscriptionRepository
                .findByIsTokenBasedTrue();

        for (UserSubscription sub : tokenSubscriptions) {
            sub.setWeeklyAdsWatched(0);
            sub.setLastWeeklyReset(LocalDateTime.now());
            sub.setExpiresAt(LocalDateTime.now().plusWeeks(1));
            subscriptionRepository.save(sub);
            radiusService.updateUserExpirationRadCheck(sub);
        }
    }

    public void incrementAdsWatched(User user) {
        UserSubscription subscription = subscriptionRepository
                .findFirstByUserOrderByCreatedAtDesc(user);

        if (subscription != null && Boolean.TRUE.equals(subscription.getIsTokenBased())) {
            subscription.setWeeklyAdsWatched(subscription.getWeeklyAdsWatched() + 1);
            subscriptionRepository.save(subscription);
        }
    }
}