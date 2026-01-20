package com.orbvpn.api.service.subscription;

import com.orbvpn.api.domain.dto.BulkSubscription;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.event.SubscriptionChangedEvent;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.RadiusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionCreator {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final RadiusService radiusService;
    private final GroupService groupService;
    private final ApplicationEventPublisher eventPublisher;

    public UserSubscription createFromPayment(Payment payment) {
        User user = payment.getUser();
        Group group = groupService.getById(payment.getGroupId());

        UserSubscription subscription = createBasicSubscription(user, group);
        subscription.setPayment(payment);

        saveAndInitialize(subscription, user);
        publishEvent(user, subscription, "NEW_SUBSCRIPTION");

        return subscription;
    }

    public UserSubscription createByAdmin(User user, Group group) {
        UserSubscription subscription = createBasicSubscription(user, group);

        saveAndInitialize(subscription, user);
        publishEvent(user, subscription, "NEW_SUBSCRIPTION", user.getPassword());

        return subscription;
    }

    public UserSubscription createBulkSubscription(User user, BulkSubscription bulkSubscription) {
        Group group = groupService.getById(bulkSubscription.getGroupId());
        UserSubscription subscription = createBasicSubscriptionWithBulkDetails(user, group, bulkSubscription);

        saveAndInitialize(subscription, user);
        publishEvent(user, subscription, "NEW_SUBSCRIPTION");

        return subscription;
    }

    private UserSubscription createBasicSubscription(User user, Group group) {
        UserSubscription subscription = new UserSubscription();
        subscription.setUser(user);
        subscription.setGroup(group);
        subscription.setDuration(group.getDuration());
        subscription.setDailyBandwidth(group.getDailyBandwidth());
        subscription.setDownloadUpload(group.getDownloadUpload());
        subscription.setMultiLoginCount(group.getMultiLoginCount());
        subscription.setExpiresAt(LocalDateTime.now().plusDays(group.getDuration()));
        return subscription;
    }

    private UserSubscription createBasicSubscriptionWithBulkDetails(User user, Group group,
            BulkSubscription bulkSubscription) {
        UserSubscription subscription = new UserSubscription();
        subscription.setUser(user);
        subscription.setGroup(group);

        // Use bulk subscription details if provided, otherwise use group defaults
        int duration = bulkSubscription.getDuration() != null ? bulkSubscription.getDuration() : group.getDuration();
        int multiLoginCount = bulkSubscription.getMultiLoginCount() != null ? bulkSubscription.getMultiLoginCount()
                : group.getMultiLoginCount();

        subscription.setDuration(duration);
        subscription.setDailyBandwidth(group.getDailyBandwidth());
        subscription.setDownloadUpload(group.getDownloadUpload());
        subscription.setMultiLoginCount(multiLoginCount);
        subscription.setExpiresAt(LocalDateTime.now().plusDays(duration));

        return subscription;
    }

    private void saveAndInitialize(UserSubscription subscription, User user) {
        userSubscriptionRepository.deleteByUserId(user.getId());
        userSubscriptionRepository.flush();
        userSubscriptionRepository.save(subscription);
        radiusService.deleteUserRadChecks(user);
        radiusService.createUserRadChecks(subscription);
    }

    private void publishEvent(User user, UserSubscription subscription,
            String eventType, String... additionalData) {
        SubscriptionChangedEvent event;
        if (additionalData.length > 0) {
            event = new SubscriptionChangedEvent(this, user, subscription, eventType, additionalData[0]);
        } else {
            event = new SubscriptionChangedEvent(this, user, subscription, eventType);
        }
        eventPublisher.publishEvent(event);
    }
}
