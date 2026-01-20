package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.ExtraLoginsPlan;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserExtraLogins;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.UserExtraLoginsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtraLoginsHelper {
    private final UserExtraLoginsRepository extraLoginsRepository;
    private final RadiusService radiusService;
    private final AsyncNotificationHelper asyncNotificationHelper;

    private static final int[] EXPIRATION_REMINDER_DAYS = { 1, 3, 7 }; // Days before expiration to send reminders

    @Transactional
    public void addExtraLogins(User user, ExtraLoginsPlan plan, int quantity) {
        log.info("Adding {} extra logins from plan {} for user {}",
                quantity, plan.getName(), user.getEmail());

        UserExtraLogins extraLogins = new UserExtraLogins();
        extraLogins.setUser(user);
        extraLogins.setPlan(plan);
        extraLogins.setLoginCount(plan.getLoginCount() * quantity);
        extraLogins.setActive(true);
        extraLogins.setStartDate(LocalDateTime.now());

        if (plan.getDurationDays() > 0) {
            extraLogins.setExpiryDate(LocalDateTime.now().plusDays(plan.getDurationDays()));
        }

        extraLogins = extraLoginsRepository.save(extraLogins);

        // Update the user's total login count in the radius service
        updateUserTotalLoginCount(user);

        // Send notifications asynchronously
        asyncNotificationHelper.sendExtraLoginsConfirmationAsync(user, extraLogins);

        // Trigger webhook event asynchronously
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("planId", plan.getId());
        extraData.put("planName", plan.getName());
        extraData.put("quantity", quantity);
        extraData.put("loginCount", extraLogins.getLoginCount());
        extraData.put("expiryDate", extraLogins.getExpiryDate());

        asyncNotificationHelper.sendUserWebhookWithExtraAsync(user, "EXTRA_LOGINS_PURCHASED", extraData);

        log.info("Successfully added extra logins for user: {}", user.getEmail());
    }

    @Transactional
    public void giftExtraLogins(User sender, User recipient, ExtraLoginsPlan plan, int quantity) {
        log.info("Processing gift of {} extra logins from {} to {}",
                quantity, sender.getEmail(), recipient.getEmail());

        UserExtraLogins gift = new UserExtraLogins();
        gift.setUser(recipient);
        gift.setPlan(plan);
        gift.setLoginCount(plan.getLoginCount() * quantity);
        gift.setGiftedBy(sender);
        gift.setActive(true);
        gift.setStartDate(LocalDateTime.now());

        if (plan.getDurationDays() > 0) {
            gift.setExpiryDate(LocalDateTime.now().plusDays(plan.getDurationDays()));
        }

        gift = extraLoginsRepository.save(gift);

        // Update recipient's total login count
        updateUserTotalLoginCount(recipient);

        // Send notifications asynchronously
        asyncNotificationHelper.sendExtraLoginsGiftNotificationAsync(sender, recipient, gift);

        // Trigger webhook event asynchronously
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("planId", plan.getId());
        extraData.put("planName", plan.getName());
        extraData.put("quantity", quantity);
        extraData.put("loginCount", gift.getLoginCount());
        extraData.put("expiryDate", gift.getExpiryDate());
        extraData.put("senderId", sender.getId());
        extraData.put("recipientId", recipient.getId());

        asyncNotificationHelper.sendUserWebhookWithExtraAsync(recipient, "EXTRA_LOGINS_GIFTED", extraData);

        log.info("Successfully processed extra logins gift from {} to {}",
                sender.getEmail(), recipient.getEmail());
    }

    @Transactional
    public void removeExtraLogins(User user, Long extraLoginsId) {
        log.info("Removing extra logins ID {} for user {}", extraLoginsId, user.getEmail());

        UserExtraLogins extraLogins = extraLoginsRepository.findById(extraLoginsId)
                .orElseThrow(() -> new NotFoundException("Extra logins not found"));

        if (!extraLogins.getUser().equals(user)) {
            log.warn("User {} attempted to remove extra logins belonging to {}",
                    user.getEmail(), extraLogins.getUser().getEmail());
            throw new IllegalArgumentException("Extra logins do not belong to this user");
        }

        extraLogins.setActive(false);
        extraLoginsRepository.save(extraLogins);

        // Update the user's total login count in the radius service
        updateUserTotalLoginCount(user);

        // Trigger webhook event asynchronously
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("extraLoginsId", extraLoginsId);
        extraData.put("planName", extraLogins.getPlan().getName());
        extraData.put("loginCount", extraLogins.getLoginCount());

        asyncNotificationHelper.sendUserWebhookWithExtraAsync(user, "EXTRA_LOGINS_REMOVED", extraData);

        log.info("Successfully removed extra logins for user: {}", user.getEmail());
    }

    @Scheduled(cron = "0 0 * * * *") // Run every hour
    @Transactional
    public void checkExpiringExtraLogins() {
        log.info("Checking for expiring extra logins");
        LocalDateTime now = LocalDateTime.now();

        // Check for logins that will expire in the specified days
        for (int days : EXPIRATION_REMINDER_DAYS) {
            LocalDateTime futureDate = now.plusDays(days);
            List<UserExtraLogins> expiringLogins = extraLoginsRepository
                    .findExpiringBetween(now, futureDate);

            for (UserExtraLogins extraLogin : expiringLogins) {
                if (extraLogin.isActive()) {
                    asyncNotificationHelper.sendExtraLoginsExpirationReminderAsync(
                            extraLogin.getUser(),
                            extraLogin,
                            days);
                }
            }
        }
    }

    @Scheduled(cron = "0 0 0 * * *") // Run daily at midnight
    @Transactional
    public void deactivateExpiredExtraLogins() {
        log.info("Processing expired extra logins");
        LocalDateTime now = LocalDateTime.now();
        List<UserExtraLogins> expiredLogins = extraLoginsRepository.findExpiredLogins(now);

        for (UserExtraLogins extraLogin : expiredLogins) {
            try {
                User user = extraLogin.getUser();

                extraLogin.setActive(false);
                extraLoginsRepository.save(extraLogin);

                updateUserTotalLoginCount(user);

                // Send notification asynchronously
                asyncNotificationHelper.sendExtraLoginsExpiredNotificationAsync(user, extraLogin);

                // Trigger webhook event asynchronously
                Map<String, Object> extraData = new HashMap<>();
                extraData.put("planName", extraLogin.getPlan().getName());
                extraData.put("loginCount", extraLogin.getLoginCount());
                extraData.put("expiryDate", extraLogin.getExpiryDate());

                asyncNotificationHelper.sendUserWebhookWithExtraAsync(user, "EXTRA_LOGINS_EXPIRED", extraData);

                log.info("Deactivated expired extra logins for user: {}", user.getEmail());

            } catch (Exception e) {
                log.error("Error processing expired extra logins for user {}: {}",
                        extraLogin.getUser().getEmail(), e.getMessage(), e);
            }
        }
    }

    private void updateUserTotalLoginCount(User user) {
        try {
            radiusService.updateUserTotalLoginCount(user);
            log.debug("Updated total login count for user: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to update total login count for user {}: {}",
                    user.getEmail(), e.getMessage(), e);
            throw e; // Rethrow to trigger transaction rollback
        }
    }

    public List<UserExtraLogins> getActiveExtraLogins(User user) {
        return extraLoginsRepository.findActiveAndValidByUser(user);
    }

    public int getTotalExtraLoginCount(User user) {
        return extraLoginsRepository.getTotalActiveLoginCount(user);
    }

    public boolean hasActiveExtraLogins(User user) {
        return getTotalExtraLoginCount(user) > 0;
    }

    public LocalDateTime getNextExpirationDate(User user) {
        List<UserExtraLogins> activeLogins = getActiveExtraLogins(user);
        return activeLogins.stream()
                .map(UserExtraLogins::getExpiryDate)
                .filter(date -> date != null && date.isAfter(LocalDateTime.now()))
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    public long getDaysUntilNextExpiration(User user) {
        LocalDateTime nextExpiration = getNextExpirationDate(user);
        if (nextExpiration != null) {
            return ChronoUnit.DAYS.between(LocalDateTime.now(), nextExpiration);
        }
        return -1;
    }
}