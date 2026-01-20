package com.orbvpn.api.service.subscription;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserProfile;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.TokenBalance;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import com.orbvpn.api.event.SubscriptionChangedEvent;
import com.orbvpn.api.domain.dto.BulkSubscription;
import com.orbvpn.api.domain.dto.UserSubscriptionView;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.mapper.UserSubscriptionViewMapper;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.repository.PaymentRepository;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.RadiusService;
import com.orbvpn.api.service.AdTokenServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.time.LocalDate;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserSubscriptionService implements SubscriptionProvider {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final RadiusService radiusService;
    private final UserSubscriptionViewMapper userSubscriptionViewMapper;
    private final GroupService groupService;
    private final ApplicationEventPublisher eventPublisher;
    private final AdTokenServiceImpl AdTokenService;
    private final PaymentRepository paymentRepository;

    public UserSubscriptionView getUserSubscription(User user) {
        UserSubscription subscription = getCurrentSubscription(user);
        if (subscription == null) {
            throw new NotFoundException("User subscription not found for user ID: " + user.getId());
        }
        return userSubscriptionViewMapper.toView(subscription);
    }

    @Override // Add this annotation
    public UserSubscription getCurrentSubscription(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        return userSubscriptionRepository.findFirstByUserOrderByCreatedAtDesc(user);
    }

    public void updateSubscriptionMultiLoginCount(User user, int multiLoginCount) {
        log.info("Updating multiLoginCount to {} for user {}", multiLoginCount, user.getId());
        UserSubscription subscription = getCurrentSubscription(user);
        if (subscription == null) {
            throw new NotFoundException("No active subscription found for user: " + user.getId());
        }
        subscription.setMultiLoginCount(multiLoginCount);
        userSubscriptionRepository.save(subscription);
        radiusService.editUserMoreLoginCount(user, multiLoginCount);
    }

    public void saveUserSubscription(UserSubscription subscription) {
        userSubscriptionRepository.save(subscription);
    }

    @Transactional
    public void deleteUserSubscriptions(User user) {
        try {
            // First detach subscriptions from subscription_history
            List<UserSubscription> subscriptions = userSubscriptionRepository.findByUser(user);
            for (UserSubscription subscription : subscriptions) {
                // Clear subscription histories
                if (subscription.getSubscriptionHistories() != null) {
                    subscription.getSubscriptionHistories().clear();
                    userSubscriptionRepository.save(subscription);
                }
            }

            // Detach payments
            userSubscriptionRepository.detachPayments(user.getId());

            // Then delete the subscriptions
            userSubscriptionRepository.deleteByUserId(user.getId());
            userSubscriptionRepository.flush();

            // Clear radius checks
            radiusService.deleteUserRadChecks(user);
        } catch (Exception e) {
            log.error("Error deleting subscriptions for user {}: {}", user.getId(), e.getMessage());
            throw new RuntimeException("Failed to delete subscriptions", e);
        }
    }

    public UserSubscription createBulkSubscription(User user, BulkSubscription bulkSubscription) {
        log.info("Creating bulk subscription for user {} with group {}",
                user.getId(), bulkSubscription.getGroupId());

        Group group = groupService.getById(bulkSubscription.getGroupId());

        UserSubscription subscription = new UserSubscription();
        subscription.setUser(user);
        subscription.setGroup(group);
        subscription.setMultiLoginCount(group.getMultiLoginCount());
        subscription.setExpiresAt(bulkSubscription.getExpiresAt());
        subscription.setDuration(group.getDuration());
        subscription.setDailyBandwidth(group.getDailyBandwidth());
        subscription.setDownloadUpload(group.getDownloadUpload());

        userSubscriptionRepository.deleteByUserId(user.getId());
        userSubscriptionRepository.flush();

        // Save the subscription
        UserSubscription savedSubscription = save(subscription);

        // Create radius checks
        radiusService.createUserRadChecks(savedSubscription);

        // Trigger webhook event
        eventPublisher.publishEvent(new SubscriptionChangedEvent(
                this, user, savedSubscription, "SUBSCRIPTION_CREATED"));

        return savedSubscription;
    }

    // Add these methods
    public List<UserProfile> getUsersExpireBetween(LocalDateTime startTime, LocalDateTime endTime) {
        return userSubscriptionRepository.getUsersExpireBetween(startTime, endTime);
    }

    public List<UserProfile> getUsersExpireAt(LocalDate localDate) {
        LocalDateTime startTime = localDate.atStartOfDay();
        LocalDateTime endTime = localDate.plusDays(1).atStartOfDay();
        return getUsersExpireBetween(startTime, endTime);
    }

    public List<UserProfile> getUsersExpireInNextDays(Integer dayCount) {
        LocalDate localDate = LocalDate.now().plusDays(dayCount);
        return getUsersExpireAt(localDate);
    }

    public List<UserProfile> getUsersExpireInPreviousDays(Integer dayCount) {
        LocalDate localDate = LocalDate.now().minusDays(dayCount);
        return getUsersExpireAt(localDate);
    }

    // Update these methods to include token-based logic

    public boolean isSubscriptionValid(User user) {
        UserSubscription subscription = getCurrentSubscription(user);

        if (subscription != null && subscription.getPayment() != null) {
            return subscription.getExpiresAt().isAfter(LocalDateTime.now());
        }

        return AdTokenService.hasValidSubscription(user.getId());
    }

    public boolean canConnect(User user) {
        UserSubscription subscription = getCurrentSubscription(user);

        if (subscription != null && subscription.getPayment() != null) {
            boolean isValid = subscription.getExpiresAt().isAfter(LocalDateTime.now());
            if (!isValid) {
                eventPublisher.publishEvent(new SubscriptionChangedEvent(
                        this, user, subscription, "SUBSCRIPTION_EXPIRED"));
            }
            return isValid;
        }

        TokenBalance balance = AdTokenService.getBalance(user.getId());
        if (balance.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        return AdTokenService.hasDailySubscription(user.getId());
    }

    @Transactional
    public UserSubscription createUserSubscription(Payment payment) {
        log.info("Creating user subscription from payment {}", payment != null ? payment.getId() : "null");

        if (payment == null) {
            throw new IllegalArgumentException("Payment cannot be null");
        }

        try {
            // Save payment first if it's not already saved
            if (payment.getId() == null) {
                payment = paymentRepository.save(payment);
            }

            User user = payment.getUser();
            Group group = groupService.getById(payment.getGroupId());

            // Delete existing subscriptions
            deleteUserSubscriptions(user);

            UserSubscription subscription = new UserSubscription();
            subscription.setUser(user);
            subscription.setGroup(group);
            subscription.setPayment(payment); // This is where the issue occurs
            subscription.setMultiLoginCount(payment.getMoreLoginCount());
            subscription.setExpiresAt(payment.getExpiresAt());
            subscription.setDuration(group.getDuration());
            subscription.setDailyBandwidth(group.getDailyBandwidth());
            subscription.setDownloadUpload(group.getDownloadUpload());
            subscription.setGateway(payment.getGateway());
            subscription.setPrice(payment.getPrice());

            // Set additional fields based on gateway
            if (payment.getGateway() == GatewayName.APPLE_STORE) {
                subscription.setOriginalTransactionId(payment.getPaymentId());
            } else if (payment.getGateway() == GatewayName.GOOGLE_PLAY) {
                subscription.setPurchaseToken(payment.getPaymentId());
            }

            // Delete existing subscriptions
            userSubscriptionRepository.deleteByUserId(user.getId());
            userSubscriptionRepository.flush();

            // Save the subscription
            UserSubscription savedSubscription = save(subscription);

            // Create radius checks
            radiusService.createUserRadChecks(savedSubscription);

            // Trigger events
            eventPublisher.publishEvent(new SubscriptionChangedEvent(
                    this, user, savedSubscription, "SUBSCRIPTION_CREATED"));
            eventPublisher.publishEvent(new SubscriptionChangedEvent(
                    this, user, savedSubscription, "NEW_SUBSCRIPTION"));

            return savedSubscription;

        } catch (Exception e) {
            log.error("Error creating subscription from payment: {}", payment.getId(), e);
            throw new RuntimeException("Failed to create subscription", e);
        }
    }

    @Transactional
    public UserSubscription save(UserSubscription subscription) {
        if (subscription.getVersion() == null) {
            subscription.setVersion(0L);
        }

        // Ensure payment is saved first if it exists
        if (subscription.getPayment() != null && subscription.getPayment().getId() == null) {
            subscription.setPayment(paymentRepository.save(subscription.getPayment()));
        }

        return userSubscriptionRepository.save(subscription);
    }

    @Transactional
    public UserSubscription createSubscriptionByAdmin(User user, Group group) {
        validateSubscriptionData(user, group);
        log.info("Creating admin subscription for user {} with group {}",
                user.getId(), group.getId());

        UserSubscription subscription = new UserSubscription();
        subscription.setUser(user);
        subscription.setGroup(group);
        subscription.setMultiLoginCount(group.getMultiLoginCount());
        subscription.setExpiresAt(LocalDateTime.now().plusDays(group.getDuration()));
        subscription.setDuration(group.getDuration());
        subscription.setDailyBandwidth(group.getDailyBandwidth());
        subscription.setDownloadUpload(group.getDownloadUpload());
        subscription.setGateway(GatewayName.FREE);
        subscription.setPrice(group.getPrice());
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        userSubscriptionRepository.deleteByUserId(user.getId());
        userSubscriptionRepository.flush();

        // Save the subscription
        UserSubscription savedSubscription = save(subscription);

        // Create radius checks
        radiusService.createUserRadChecks(savedSubscription);

        // Add event publishing
        eventPublisher.publishEvent(new SubscriptionChangedEvent(
                this, user, savedSubscription, "NEW_SUBSCRIPTION", user.getPassword()));

        return savedSubscription;
    }

    @Transactional
    public void cleanup(User user) {
        log.info("Cleaning up subscriptions for user: {}", user.getId());
        userSubscriptionRepository.deleteByUserId(user.getId());
        radiusService.deleteUserRadChecks(user);
        log.info("Successfully cleaned up subscriptions for user: {}", user.getId());
    }

    private void validateSubscriptionData(User user, Group group) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
    }
}
