package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.CoinPayment;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.repository.RadAcctRepository;
import com.orbvpn.api.service.reseller.ResellerLevelService;
import com.orbvpn.api.repository.UnverifiedUserRepository;
import com.orbvpn.api.repository.VerificationTokenRepository;
import com.orbvpn.api.repository.CoinPaymentRepository;
import com.orbvpn.api.repository.PaymentRepository;
import com.orbvpn.api.repository.ProcessedAppleNotificationRepository;
import com.orbvpn.api.repository.ProcessedGoogleNotificationRepository;
import com.orbvpn.api.repository.ProcessedStripeWebhookEventRepository;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.service.subscription.RenewUserSubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import java.util.List;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobService {

  private static final int DAILY_RATE = 24 * 60 * 60 * 1000;
  private static final int HOUR_RATE = 60 * 60 * 1000;
  private static final int TEN_MINUTES_RATE = 10 * 60 * 1000;
  private static final int FIVE_MINUTES_RATE = 5 * 60 * 1000;

  // Staggered midnight cleanup schedules to avoid database contention
  private static final String CLEANUP_UNVERIFIED_USERS = "0 0 0 * * ?";   // 00:00
  private static final String CLEANUP_APPLE_NOTIFICATIONS = "0 10 0 * * ?"; // 00:10
  private static final String CLEANUP_GOOGLE_NOTIFICATIONS = "0 20 0 * * ?"; // 00:20
  private static final String CLEANUP_STRIPE_WEBHOOKS = "0 30 0 * * ?";   // 00:30

  private final HelpCenterService helpCenterService;
  private final PromotionService promotionService;
  private final ResellerLevelService resellerLevelService;
  private final RenewUserSubscriptionService renewUserSubscriptionService;
  private final MoreLoginCountService moreLoginCountService;
  private final RadAcctRepository radAcctRepository;
  private final ConnectionService connectionService;
  private final UnverifiedUserRepository unverifiedUserRepository;
  private final VerificationTokenRepository verificationTokenRepository;
  private final CoinPaymentRepository coinPaymentRepository;
  private final PaymentRepository paymentRepository;
  private final ProcessedAppleNotificationRepository processedAppleNotificationRepository;
  private final ProcessedGoogleNotificationRepository processedGoogleNotificationRepository;
  private final ProcessedStripeWebhookEventRepository processedStripeWebhookEventRepository;

  @Scheduled(fixedRate = HOUR_RATE)
  public void removeOldTickets() {
    log.info("Starting job for removing old tickets");
    helpCenterService.removeOldTickets();
    log.info("Removing old tickets job is finished");
  }

  @Scheduled(fixedRate = HOUR_RATE)
  public void updateResellerLevels() {
    log.info("Starting job for updating reseller level");
    resellerLevelService.updateResellersLevel();
    log.info("Finished job for updating reseller level");
  }

  @Scheduled(fixedRate = HOUR_RATE)
  public void renewSubscriptions() {
    log.info("Started renewing user subscriptions");
    renewUserSubscriptionService.renewSubscriptions();
    log.info("Finished job renewing subscriptions");
  }

  @Scheduled(fixedRate = HOUR_RATE)
  public void removeExpiredMoreLoginCount() {
    log.info("Started removing expired moore login count");
    moreLoginCountService.removeExpiredMoreLoginCount();
    log.info("Finished removing expired more login count");
  }

  @Scheduled(fixedRate = FIVE_MINUTES_RATE)
  public void removeAllRadacctTemporarily() {
    // https://freeradius-users.freeradius.narkive.com/5ULrgWHb/user-freezing
    log.info("Started removing all radacct records");
    radAcctRepository.deleteAllInBatch();
    moreLoginCountService.removeExpiredMoreLoginCount();
    log.info("Finished removing all radacct records");
  }

  @Scheduled(fixedRate = TEN_MINUTES_RATE)
  public void disconnectDeactivatedUsers() {
    log.info("Started Disconnecting Deactivated Users");
    connectionService.disconnectDeactivatedUsers();
    log.info("Finished Disconnecting Deactivated Users");
  }

  @Scheduled(fixedRate = DAILY_RATE)
  public void promotions() {
    log.info("Starting job for promotions");
    promotionService.runTask();
    log.info("promotions job is finished");
  }

  @Scheduled(cron = "0 0 * * * *") // Every hour
  public void cleanupExpiredCoinPayments() {
    List<CoinPayment> expiredPayments = coinPaymentRepository
        .findByStatusAndCreatedAtBefore(false, LocalDateTime.now().minusHours(24));
    for (CoinPayment payment : expiredPayments) {
      payment.setStatus(false);
      payment.getPayment().setStatus(PaymentStatus.EXPIRED);
      coinPaymentRepository.save(payment);
    }
  }

  @Transactional
  @Scheduled(cron = CLEANUP_UNVERIFIED_USERS)
  @SchedulerLock(name = "cleanupOldUnverifiedUsers", lockAtLeastFor = "5m", lockAtMostFor = "30m")
  public void cleanupOldUnverifiedUsers() {
    LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
    log.info("Started cleaning up old unverified users");

    // Find old unverified users based on a threshold (e.g., entries older than a
    // week)
    var oldUnverifiedUsers = unverifiedUserRepository.findAllByCreatedAtBefore(oneWeekAgo);
    // Delete associated tokens
    oldUnverifiedUsers.forEach(user -> verificationTokenRepository.deleteByUser(user));
    // Delete old unverified users
    unverifiedUserRepository.deleteAll(oldUnverifiedUsers);

    log.info("Finished cleaning up old unverified users");
  }

  @Transactional
  @Scheduled(cron = "0 0 * * * *") // Every hour
  public void cleanupExpiredPendingPayments() {
    log.info("Started cleaning up expired pending payments");

    // Mark pending payments older than 24 hours as expired
    LocalDateTime expireThreshold = LocalDateTime.now().minusHours(24);
    List<Payment> expiredPayments = paymentRepository.findExpiredPendingPayments(expireThreshold);

    int expiredCount = 0;
    for (Payment payment : expiredPayments) {
      payment.setStatus(PaymentStatus.EXPIRED);
      payment.setErrorMessage("Payment expired - no confirmation received within 24 hours");
      paymentRepository.save(payment);
      expiredCount++;
    }

    log.info("Finished cleaning up expired pending payments. Expired {} payments", expiredCount);
  }

  @Transactional
  @Scheduled(cron = CLEANUP_APPLE_NOTIFICATIONS)
  @SchedulerLock(name = "cleanupOldProcessedAppleNotifications", lockAtLeastFor = "5m", lockAtMostFor = "30m")
  public void cleanupOldProcessedAppleNotifications() {
    log.info("Started cleaning up old processed Apple notifications");

    // Keep records for 7 days for debugging, then delete
    LocalDateTime cutoffTime = LocalDateTime.now().minusDays(7);
    int deletedCount = processedAppleNotificationRepository.deleteByProcessedAtBefore(cutoffTime);

    log.info("Finished cleaning up old processed Apple notifications. Deleted {} records", deletedCount);
  }

  @Transactional
  @Scheduled(cron = CLEANUP_GOOGLE_NOTIFICATIONS)
  @SchedulerLock(name = "cleanupOldProcessedGoogleNotifications", lockAtLeastFor = "5m", lockAtMostFor = "30m")
  public void cleanupOldProcessedGoogleNotifications() {
    log.info("Started cleaning up old processed Google Play notifications");

    // Keep records for 7 days for debugging, then delete
    LocalDateTime cutoffTime = LocalDateTime.now().minusDays(7);
    int deletedCount = processedGoogleNotificationRepository.deleteByProcessedAtBefore(cutoffTime);

    log.info("Finished cleaning up old processed Google Play notifications. Deleted {} records", deletedCount);
  }

  @Transactional
  @Scheduled(cron = CLEANUP_STRIPE_WEBHOOKS)
  @SchedulerLock(name = "cleanupOldProcessedStripeWebhookEvents", lockAtLeastFor = "5m", lockAtMostFor = "30m")
  public void cleanupOldProcessedStripeWebhookEvents() {
    log.info("Started cleaning up old processed Stripe webhook events");

    // Keep records for 7 days for debugging, then delete
    LocalDateTime cutoffTime = LocalDateTime.now().minusDays(7);
    int deletedCount = processedStripeWebhookEventRepository.deleteByProcessedAtBefore(cutoffTime);

    log.info("Finished cleaning up old processed Stripe webhook events. Deleted {} records", deletedCount);
  }
}
