package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.SubscriptionHistory;
import com.orbvpn.api.domain.entity.TransactionUserMapping;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.NotificationPreferencesRepository;
import com.orbvpn.api.repository.OauthTokenRepository;
import com.orbvpn.api.repository.PasswordResetRepository;
import com.orbvpn.api.repository.SubscriptionHistoryRepository;
import com.orbvpn.api.repository.TransactionUserMappingRepository;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.event.UserActionEvent;
import com.orbvpn.api.service.payment.PaymentService;
import com.orbvpn.api.service.subscription.UserSubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import jakarta.persistence.EntityManager;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserCleanupService {

    private final TransactionUserMappingRepository transactionUserMappingRepository;
    private final NotificationPreferencesRepository notificationPreferencesRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final SubscriptionHistoryRepository subscriptionHistoryRepository;
    private final OauthTokenRepository oauthTokenRepository;
    private final UserRepository userRepository;
    private final UserDeviceService userDeviceService;
    private final UserSubscriptionService userSubscriptionService;
    private final PaymentService paymentService;
    private final RadiusService radiusService;
    private final EntityManager entityManager;

    @EventListener
    @Transactional
    public void handleUserEvent(UserActionEvent event) {
        User user = event.getUser();
        String action = event.getAction();

        try {
            switch (action) {
                case UserActionEvent.PASSWORD_RESET:
                case UserActionEvent.PASSWORD_CHANGED:
                case UserActionEvent.PASSWORD_REENCRYPTED:
                case UserActionEvent.USER_SOFT_DELETED:
                    log.info("Handling user action: {} for user ID: {}", action, user.getId());
                    userDeviceService.deleteUserDevices(user);
                    break;
                case UserActionEvent.USER_DELETED:
                case UserActionEvent.USER_ACCOUNT_DELETED:
                    log.info("Handling deletion action: {} for user ID: {}", action, user.getId());
                    dissociateAndCleanup(user);
                    break;
                default:
                    log.warn("Unhandled user action: {} for user ID: {}", action, user.getId());
            }
        } catch (Exception e) {
            log.error("Error handling user event: {} for user ID: {}", action, user.getId(), e);
            throw e;
        }
    }

    @Transactional
    public void dissociateAndCleanup(User user) {
        try {
            log.info("Starting cleanup process for user ID: {}", user.getId());

            // Step 1: Delete notification preferences first (most dependent)
            notificationPreferencesRepository.deleteByUser(user);
            log.debug("Deleted notification preferences for user ID: {}", user.getId());

            // Step 2: Delete password resets
            passwordResetRepository.deleteAllByUser(user);
            log.debug("Deleted password reset entries for user ID: {}", user.getId());

            // Step 3: Delete trial history
            entityManager.createQuery("DELETE FROM TrialHistory th WHERE th.userId = :userId")
                    .setParameter("userId", user.getId())
                    .executeUpdate();
            log.debug("Deleted trial history for user ID: {}", user.getId());

            // Step 4: Delete OAuth tokens
            oauthTokenRepository.deleteByUser(user);
            log.debug("Deleted OAuth tokens for user ID: {}", user.getId());

            // Step 5: Delete user devices
            userDeviceService.deleteUserDevices(user);
            log.debug("Deleted user devices for user ID: {}", user.getId());

            // Step 6: Archive subscription history (doesn't delete, just updates)
            archiveSubscriptionHistory(user);
            log.debug("Archived subscription history for user ID: {}", user.getId());

            // Step 7: Dissociate transaction mappings (updates, doesn't delete)
            dissociateTransactionMappings(user);
            log.debug("Dissociated transaction mappings for user ID: {}", user.getId());

            // Step 8: Delete subscriptions
            userSubscriptionService.deleteUserSubscriptions(user);
            log.debug("Deleted user subscriptions for user ID: {}", user.getId());

            // Step 9: Delete payments
            paymentService.deleteUserPayments(user);
            log.debug("Deleted payments for user ID: {}", user.getId());

            // Step 10: Delete radius data
            radiusService.deleteUserRadChecks(user);
            radiusService.deleteUserRadAcct(user);
            log.debug("Deleted radius data for user ID: {}", user.getId());

            // Flush any pending changes
            entityManager.flush();

            log.info("Successfully completed cleanup process for user ID: {}", user.getId());

        } catch (Exception e) {
            log.error("Error during cleanup process for user ID: {}", user.getId(), e);
            throw new RuntimeException("Failed to cleanup user data", e);
        }
    }

    private void archiveSubscriptionHistory(User user) {
        try {
            log.debug("Archiving subscription history for user ID: {}", user.getId());
            List<SubscriptionHistory> histories = subscriptionHistoryRepository.findByUserId(user.getId());
            for (SubscriptionHistory history : histories) {
                history.archiveForDeletedUser();
                subscriptionHistoryRepository.save(history);
            }
            log.debug("Successfully archived {} subscription history records for user ID: {}",
                    histories.size(), user.getId());
        } catch (Exception e) {
            log.error("Error archiving subscription history for user ID: {}", user.getId(), e);
            throw e;
        }
    }

    private void dissociateTransactionMappings(User user) {
        try {
            log.debug("Dissociating transaction mappings for user ID: {}", user.getId());
            List<TransactionUserMapping> mappings = transactionUserMappingRepository.findByUser(user);
            for (TransactionUserMapping mapping : mappings) {
                mapping.setUser(null);
                transactionUserMappingRepository.save(mapping);
            }
            log.debug("Successfully dissociated {} transaction mappings for user ID: {}",
                    mappings.size(), user.getId());
        } catch (Exception e) {
            log.error("Error dissociating transaction mappings for user ID: {}", user.getId(), e);
            throw e;
        }
    }

    @Transactional
    public void deleteUserByOauthId(String oauthId) {
        try {
            log.info("Attempting to delete user by OAuth ID: {}", oauthId);
            User user = userRepository.findByOauthId(oauthId)
                    .orElseThrow(() -> new NoSuchElementException("User not found with OAuth ID: " + oauthId));

            // Perform cleanup
            dissociateAndCleanup(user);

            // Delete the user
            userRepository.delete(user);

            log.info("Successfully deleted user with OAuth ID: {}", oauthId);
        } catch (Exception e) {
            log.error("Error deleting user with OAuth ID: {}", oauthId, e);
            throw e;
        }
    }
}
