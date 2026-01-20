package com.orbvpn.api.service.payment;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.PaymentCategory;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.domain.enums.ResellerLevelName;
import com.orbvpn.api.domain.payload.CoinPayment.AddressResponse;
import com.orbvpn.api.domain.payload.CoinPayment.CoinPaymentResponse;
import com.orbvpn.api.exception.InsufficientFundsException;
import com.orbvpn.api.exception.PaymentException;
import com.orbvpn.api.repository.PaymentRepository;
import com.orbvpn.api.repository.ResellerRepository;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.InvoiceService;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.RadiusService;
import com.orbvpn.api.service.MoreLoginCountService;
import com.orbvpn.api.service.subscription.AppleService;

import com.orbvpn.api.service.payment.coinpayment.CoinPaymentService;
import com.orbvpn.api.service.payment.nowpayment.NowPaymentService;
import com.orbvpn.api.domain.payload.NowPayment.NowPaymentResponse;
import com.orbvpn.api.utils.ThirdAPIUtils;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.DAYS;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PaymentService {

  private final UserSubscriptionService userSubscriptionService;
  private final MoreLoginCountService moreLoginCountService;
  private final StripeService stripeService;
  private final PaypalService paypalService;
  private final CoinPaymentService coinpaymentService;
  private final NowPaymentService nowPaymentService;
  private final AppleService appleService;
  private final RadiusService radiusService;
  private final GroupService groupService;
  private final ParspalService parspalService;
  private final InvoiceService invoiceService;
  private final ThirdAPIUtils apiUtil;
  private final PaymentRepository paymentRepository;
  private final PaymentUserService paymentUserService;
  private final UserService userService;
  private final ResellerRepository resellerRepository;

  public void deleteUserPayments(User user) {
    paymentUserService.deleteUserPayments(user);
  }

  @Transactional
  public void fullFillPayment(GatewayName gateway, String paymentId) {
    // Use pessimistic lock to prevent double fulfillment from concurrent webhooks
    Payment payment = paymentRepository
        .findByGatewayAndPaymentIdWithLock(gateway, paymentId)
        .orElseThrow(() -> new RuntimeException("Payment not found"));

    fullFillPaymentInternal(payment);
  }

  @Transactional
  public void fullFillPayment(Payment payment) {
    // Re-fetch with lock to prevent race conditions
    Payment lockedPayment = paymentRepository.findByIdWithLock(payment.getId())
        .orElseThrow(() -> new RuntimeException("Payment not found"));

    fullFillPaymentInternal(lockedPayment);
  }

  /**
   * Internal method that performs the actual fulfillment.
   * Caller must ensure payment is locked before calling this method.
   */
  private void fullFillPaymentInternal(Payment payment) {
    // Double-check status after acquiring lock
    if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
      log.info("Payment {} already fulfilled, skipping", payment.getId());
      return; // Idempotent - don't throw, just return
    }

    if (payment.getStatus() == PaymentStatus.FAILED) {
      throw new PaymentException("Cannot fulfill a failed payment");
    }

    try {
      if (payment.getCategory() == PaymentCategory.GROUP) {
        handleGroupPayment(payment);
      } else if (payment.getCategory() == PaymentCategory.MORE_LOGIN) {
        handleMoreLoginPayment(payment);
      }

      payment.setStatus(PaymentStatus.SUCCEEDED);
      paymentRepository.save(payment);

      log.info("Payment {} fulfilled successfully", payment.getId());
    } catch (Exception e) {
      log.error("Error fulfilling payment {}", payment.getId(), e);
      payment.setStatus(PaymentStatus.FAILED);
      payment.setErrorMessage(e.getMessage());
      paymentRepository.save(payment);
      throw new PaymentException("Payment fulfillment failed", e);
    }
  }

  @Transactional
  public Payment renewPayment(Payment payment) throws Exception {
    Payment newPayment = Payment.builder()
        .user(payment.getUser())
        .status(PaymentStatus.PENDING)
        .gateway(payment.getGateway())
        .category(payment.getCategory())
        .price(payment.getPrice())
        .groupId(payment.getGroupId())
        .renew(true)
        .renewed(true)
        .build();

    try {
      switch (payment.getGateway()) {
        case STRIPE:
          PaymentIntent paymentIntent = stripeService.renewStripePayment(newPayment);
          newPayment.setPaymentId(paymentIntent.getId());
          paymentRepository.save(newPayment);
          fullFillPayment(newPayment);
          break;

        case APPLE_STORE:
          AppleSubscriptionData subscriptionData = appleService
              .getSubscriptionData(payment.getMetaData(), "unknown", payment.getUser());
          newPayment.setPaymentId(subscriptionData.getOriginalTransactionId());
          newPayment.setExpiresAt(subscriptionData.getExpiresAt());
          newPayment.setMetaData(payment.getMetaData());
          paymentRepository.save(newPayment);
          fullFillPayment(newPayment);
          break;

        case RESELLER_CREDIT:
          Group group = groupService.getById(payment.getGroupId());
          User user = payment.getUser();

          // Lock reseller to prevent race condition on credit deduction
          Reseller reseller = resellerRepository.findByIdWithLock(user.getReseller().getId())
              .orElseThrow(() -> new PaymentException("Reseller not found"));

          // Calculate price based on group duration
          BigDecimal price = calculatePrice(reseller, group, group.getDuration());

          // Check if reseller has enough credit (with lock held)
          if (reseller.getCredit().compareTo(price) < 0) {
            throw new InsufficientFundsException("Reseller does not have enough credit");
          }

          // Deduct credit from reseller (atomic with check due to lock)
          reseller.setCredit(reseller.getCredit().subtract(price));
          resellerRepository.save(reseller);

          newPayment.setExpiresAt(LocalDateTime.now().plusDays(group.getDuration()));
          newPayment.setPaymentId(UUID.randomUUID().toString());
          newPayment.setPrice(price);
          newPayment.setGroupId(group.getId());
          newPayment.setMoreLoginCount(group.getMultiLoginCount());

          log.info("Created renewal payment for RESELLER_CREDIT - User: {}, Group: {}, Price: {}, Reseller: {}",
              user.getId(), group.getId(), price, reseller.getId());

          // Save and handle in one transaction
          paymentRepository.save(newPayment);
          handleGroupPayment(newPayment);
          newPayment.setStatus(PaymentStatus.SUCCEEDED);
          paymentRepository.save(newPayment);
          break;

        default:
          throw new PaymentException("Unsupported gateway for renewal: " + payment.getGateway());
      }

      return newPayment;
    } catch (Exception e) {
      log.error("Error renewing payment", e);
      newPayment.setStatus(PaymentStatus.FAILED);
      newPayment.setErrorMessage(e.getMessage());
      paymentRepository.save(newPayment);
      throw e;
    }
  }

  private BigDecimal calculatePrice(Reseller reseller, Group group, int days) {
    ResellerLevel level = reseller.getLevel();
    if (level.getName().equals(ResellerLevelName.OWNER)) {
      return BigDecimal.ZERO;
    }

    BigDecimal price = group.getPrice();
    BigDecimal discount = price.multiply(level.getDiscountPercent()).divide(new BigDecimal(100));
    BigDecimal dayPercent = BigDecimal.valueOf(days)
        .divide(BigDecimal.valueOf(group.getDuration()), 2, RoundingMode.HALF_UP);

    return price.subtract(discount).multiply(dayPercent);
  }

  // Stripe Payment Methods
  public StripePaymentResponse stripeCreatePayment(PaymentCategory category, int groupId,
      int moreLoginCount, boolean renew, String paymentMethodId) throws StripeException {
    Payment payment = createPayment(GatewayName.STRIPE, category, groupId, moreLoginCount, renew);
    User user = userService.getUser();
    return stripeService.createStripePayment(payment, user, paymentMethodId);
  }

  // PayPal Payment Methods
  public PaypalCreatePaymentResponse paypalCreatePayment(PaymentCategory category, int groupId, int moreLoginCount)
      throws Exception {
    Payment payment = createPayment(GatewayName.PAYPAL, category, groupId, moreLoginCount, false);
    return paypalService.createPayment(payment);
  }

  public PaypalApprovePaymentResponse paypalApprovePayment(String orderId) {
    PaypalApprovePaymentResponse approveResponse = paypalService.approvePayment(orderId);
    if (approveResponse.isSuccess()) {
      fullFillPayment(GatewayName.PAYPAL, orderId);
    }
    return approveResponse;
  }

  // Coin Payment Methods
  public CoinPaymentResponse coinpaymentCreatePayment(PaymentCategory category, int groupId, int moreLoginCount,
      String coin) throws Exception {
    Payment payment = createPayment(GatewayName.COIN_PAYMENT, category, groupId, moreLoginCount, false);
    User user = userService.getUser();

    CoinPayment coinPayment = CoinPayment.builder()
        .user(user)
        .payment(payment)
        .coin(coin)
        .build();

    return coinpaymentService.createPayment(coinPayment);
  }

  public AddressResponse coinpaymentCreatePaymentV2(PaymentCategory category, int groupId, int moreLoginCount,
      String coin) throws IOException {
    Payment payment = createPayment(GatewayName.COIN_PAYMENT, category, groupId, moreLoginCount, false);
    var user = userService.getUser();

    var usdPrice = payment.getPrice();
    var cryptoName = coin.contains(".") ? coin.split(".")[0] : coin;
    var cryptoPrice = apiUtil.getCryptoPriceBySymbol(cryptoName);
    // Use BigDecimal for precision in crypto amount calculation
    var cryptoAmount = usdPrice.divide(BigDecimal.valueOf(cryptoPrice), 8, java.math.RoundingMode.HALF_UP);

    var coinPaymentCallback = CoinPaymentCallback.builder()
        .user(user)
        .payment(payment)
        .coin(coin)
        .coinAmount(cryptoAmount)
        .build();
    return coinpaymentService.createPayment(coinPaymentCallback);
  }

  // NOWPayments Methods (replacing CoinPayments)
  public NowPaymentResponse nowPaymentCreatePayment(PaymentCategory category, int groupId, int moreLoginCount,
      String payCurrency) {
    Payment payment = createPayment(GatewayName.NOW_PAYMENT, category, groupId, moreLoginCount, false);
    User user = userService.getUser();
    log.info("Creating NOWPayment - category: {}, payCurrency: {}", category, payCurrency);
    return nowPaymentService.createPayment(user, payment, payCurrency);
  }

  // Apple Payment Methods
  public boolean appleCreatePayment(String receipt) {
    log.info("Creating payment for apple");
    User user = userService.getUser(); // Add this line
    String deviceId = "unknown"; // You'll need to get this from somewhere
    AppleSubscriptionData appleSubscriptionData = appleService.getSubscriptionData(receipt, deviceId, user);
    Payment payment = createPayment(GatewayName.APPLE_STORE, PaymentCategory.GROUP,
        appleSubscriptionData.getGroupId(), 0, true);
    payment.setPaymentId(appleSubscriptionData.getOriginalTransactionId());
    payment.setMetaData(receipt);
    payment.setExpiresAt(appleSubscriptionData.getExpiresAt());
    fullFillPayment(payment);
    log.info("Created payment for apple receipt : {}", payment);
    return true;
  }

  // Parspal Payment Methods
  public ParspalCreatePaymentResponse parspalCreatePayment(PaymentCategory category, int groupId,
      int moreLoginCount) {
    Payment payment = createPayment(GatewayName.PARSPAL, category, groupId, moreLoginCount, false);
    return parspalService.createPayment(payment);
  }

  public boolean parspalApprovePayment(String payment_id, String receipt_number) {
    boolean approved = parspalService.approvePayment(payment_id, receipt_number);
    if (approved) {
      fullFillPayment(GatewayName.PARSPAL, payment_id);
    }
    return approved;
  }

  // Payment Creation Methods
  public Payment createPayment(GatewayName gateway, PaymentCategory category, int groupId,
      int moreLoginCount, boolean renew) {
    if (category == PaymentCategory.GROUP) {
      return createGroupPayment(groupId, renew, gateway);
    }
    if (category == PaymentCategory.MORE_LOGIN) {
      return createBuyMoreLoginPayment(moreLoginCount, gateway);
    }
    if (category == PaymentCategory.BUY_CREDIT) {
      return createBuyCreditPayment(moreLoginCount, gateway);
    }
    throw new PaymentException("Not supported category");
  }

  private Payment createGroupPayment(int groupId, boolean renew, GatewayName gateway) {
    User user = userService.getUser();
    Group group = groupService.getById(groupId);

    Payment payment = Payment.builder()
        .user(user)
        .status(PaymentStatus.PENDING)
        .gateway(gateway)
        .category(PaymentCategory.GROUP)
        .moreLoginCount(group.getMultiLoginCount())
        .price(group.getPrice())
        .groupId(groupId)
        .renew(renew)
        .build();

    payment = paymentUserService.savePayment(payment); // Use new service
    invoiceService.createInvoice(payment);

    return payment;
  }

  private Payment createBuyMoreLoginPayment(int number, GatewayName gateway) {
    User user = userService.getUser();
    Payment payment = Payment.builder()
        .user(user)
        .status(PaymentStatus.PENDING)
        .gateway(gateway)
        .category(PaymentCategory.MORE_LOGIN)
        .price(getBuyMoreLoginPrice(number))
        .moreLoginCount(number)
        .build();

    invoiceService.createInvoice(payment);
    paymentRepository.save(payment);
    return payment;
  }

  private Payment createBuyCreditPayment(int number, GatewayName gateway) {
    User user = userService.getUser();
    Payment payment = Payment.builder()
        .user(user)
        .status(PaymentStatus.PENDING)
        .gateway(gateway)
        .category(PaymentCategory.BUY_CREDIT)
        .price(getCreditPrice(number))
        .moreLoginCount(number)
        .build();

    invoiceService.createInvoice(payment);
    paymentRepository.save(payment);
    return payment;
  }

  // Helper Methods
  private void handleGroupPayment(Payment payment) {
    Group group = groupService.getGroupIgnoreDelete(payment.getGroupId());
    if (payment.getExpiresAt() == null) {
      payment.setExpiresAt(LocalDateTime.now().plusDays(group.getDuration()));
    }
    userSubscriptionService.createUserSubscription(payment);
  }

  private void handleMoreLoginPayment(Payment payment) {
    User user = payment.getUser();
    UserSubscription userSubscription = userSubscriptionService.getCurrentSubscription(user);
    LocalDateTime expiresAt = userSubscription.getExpiresAt();

    int multiLoginCount = userSubscription.getMultiLoginCount() + payment.getMoreLoginCount();
    userSubscription.setMultiLoginCount(multiLoginCount);
    userSubscriptionService.save(userSubscription);
    radiusService.addUserMoreLoginCount(user, multiLoginCount);
    payment.setExpiresAt(expiresAt);

    createMoreLoginCount(payment, user, expiresAt);
  }

  private void createMoreLoginCount(Payment payment, User user, LocalDateTime expiresAt) {
    MoreLoginCount moreLoginCount = new MoreLoginCount();
    moreLoginCount.setUser(user);
    moreLoginCount.setExpiresAt(expiresAt);
    moreLoginCount.setNumber(payment.getMoreLoginCount());
    moreLoginCountService.save(moreLoginCount);
  }

  // Pricing Methods
  public BigDecimal getCreditPrice(int number) {
    return new BigDecimal(number);
  }

  /**
   * Calculate pro-rata price for additional login devices.
   * Uses the centralized DevicePricingService for correct calculation.
   *
   * Formula: (groupPrice / subscriptionDuration) / baseDevices * deviceCount * remainingDays * discount
   *
   * @param number Number of additional devices
   * @return Calculated price
   */
  public BigDecimal getBuyMoreLoginPrice(int number) {
    User user = userService.getUser();
    UserSubscription subscription = userSubscriptionService.getCurrentSubscription(user);

    if (subscription == null) {
      throw new RuntimeException("No active subscription found");
    }

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime expiresAt = subscription.getExpiresAt();

    if (expiresAt == null || expiresAt.isBefore(now)) {
      throw new RuntimeException("Subscription has expired");
    }

    long remainingDays = DAYS.between(now, expiresAt);
    if (remainingDays <= 0) {
      remainingDays = 1; // Minimum 1 day
    }

    int subscriptionDuration = subscription.getDuration();
    if (subscriptionDuration <= 0) {
      subscriptionDuration = 30; // Default to 30 days
    }

    Group group = subscription.getGroup();
    BigDecimal groupPrice = group.getPrice();
    int baseDeviceCount = Math.max(group.getMultiLoginCount(), 1);

    // Calculate daily rate per device
    BigDecimal dailySubscriptionRate = groupPrice.divide(
        BigDecimal.valueOf(subscriptionDuration), 6, RoundingMode.HALF_UP);
    BigDecimal dailyRatePerDevice = dailySubscriptionRate.divide(
        BigDecimal.valueOf(baseDeviceCount), 6, RoundingMode.HALF_UP);

    // Apply service group discount if available
    BigDecimal discountMultiplier = BigDecimal.ONE;
    ServiceGroup serviceGroup = group.getServiceGroup();
    if (serviceGroup != null && serviceGroup.getDiscount() != null) {
      BigDecimal discountPercent = serviceGroup.getDiscount();
      discountMultiplier = BigDecimal.ONE.subtract(
          discountPercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
    }

    // Calculate final price
    BigDecimal price = dailyRatePerDevice
        .multiply(BigDecimal.valueOf(number))
        .multiply(BigDecimal.valueOf(remainingDays))
        .multiply(discountMultiplier)
        .setScale(2, RoundingMode.HALF_UP);

    // Minimum charge of $0.50
    BigDecimal minimumCharge = new BigDecimal("0.50");
    if (price.compareTo(minimumCharge) < 0) {
      price = minimumCharge;
    }

    log.info("Calculated device price for user {}: {} devices, {} remaining days, price: {}",
        user.getId(), number, remainingDays, price);

    return price;
  }

  // Subscription Renewal Methods
  public List<Payment> findAllSubscriptionPaymentsToRenew() {
    List<Payment> paymentsToRenew = paymentRepository.findAllSubscriptionPaymentsToRenew(LocalDateTime.now());
    Iterator<Payment> paymentIterator = paymentsToRenew.iterator();

    while (paymentIterator.hasNext()) {
      Payment payment = paymentIterator.next();
      UserSubscription currentSubscription = userSubscriptionService.getCurrentSubscription(payment.getUser());
      if (currentSubscription.getPayment() != payment)
        paymentIterator.remove();
    }
    return paymentsToRenew;
  }

  // Webhook Methods (continued)
  public void handleStripeWebhookEvent(String eventType, String paymentIntentId) {
    if (paymentIntentId == null) {
      return;
    }

    Optional<Payment> paymentOpt = paymentRepository.findByPaymentId(paymentIntentId);
    if (paymentOpt.isEmpty()) {
      log.warn("Payment not found for Stripe paymentIntentId: {}", paymentIntentId);
      return;
    }

    Payment payment = paymentOpt.get();
    try {
      switch (eventType) {
        case "payment_intent.succeeded":
          payment.setStatus(PaymentStatus.SUCCEEDED);
          fullFillPayment(payment);
          log.info("Stripe payment succeeded: {}", paymentIntentId);
          break;

        case "payment_intent.payment_failed":
          payment.setStatus(PaymentStatus.FAILED);
          log.error("Stripe payment failed: {}", paymentIntentId);
          break;

        case "payment_intent.canceled":
          payment.setStatus(PaymentStatus.CANCELLED);
          log.info("Stripe payment cancelled: {}", paymentIntentId);
          break;

        default:
          log.info("Unhandled Stripe event type: {} for payment: {}", eventType, paymentIntentId);
      }

      paymentRepository.save(payment);
    } catch (Exception e) {
      log.error("Error handling Stripe webhook event: {} for payment: {}", eventType, paymentIntentId, e);
      payment.setStatus(PaymentStatus.FAILED);
      payment.setErrorMessage(e.getMessage());
      paymentRepository.save(payment);
    }
  }

  // Additional helper methods for payment verification
  public boolean isPaymentValid(Payment payment) {
    if (payment == null) {
      return false;
    }

    // Check if payment amount matches group price for group payments
    if (payment.getCategory() == PaymentCategory.GROUP) {
      Group group = groupService.getById(payment.getGroupId());
      return payment.getPrice().compareTo(group.getPrice()) == 0;
    }

    return payment.getPrice().compareTo(BigDecimal.ZERO) > 0;
  }

  public Payment findStripePayment(String paymentIntentId) {
    return paymentRepository.findByGatewayAndPaymentId(GatewayName.STRIPE, paymentIntentId)
        .orElse(null);
  }

  public boolean isPaymentExpired(Payment payment) {
    if (payment.getExpiresAt() == null) {
      return false;
    }
    return payment.getExpiresAt().isBefore(LocalDateTime.now());
  }

  public void cancelPayment(Payment payment, String reason) {
    if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
      throw new PaymentException("Cannot cancel a succeeded payment");
    }

    payment.setStatus(PaymentStatus.CANCELLED);
    payment.setErrorMessage(reason);
    paymentRepository.save(payment);

    log.info("Payment {} cancelled. Reason: {}", payment.getId(), reason);
  }

  public void markPaymentAsFailed(Payment payment, String errorMessage) {
    payment.setStatus(PaymentStatus.FAILED);
    payment.setErrorMessage(errorMessage);
    paymentRepository.save(payment);

    log.error("Payment {} failed. Error: {}", payment.getId(), errorMessage);
  }

  // Utility method to handle payment cleanup
  @Transactional
  public void cleanupFailedPayments() {
    List<Payment> failedPayments = paymentRepository.findByStatus(PaymentStatus.FAILED);
    for (Payment payment : failedPayments) {
      if (payment.getCreatedAt().plusDays(30).isBefore(LocalDateTime.now())) {
        log.info("Cleaning up failed payment: {}", payment.getId());
        paymentRepository.delete(payment);
      }
    }
  }

  // Method to handle payment retries
  @Transactional
  public Payment retryPayment(Payment failedPayment) {
    if (failedPayment.getStatus() != PaymentStatus.FAILED) {
      throw new PaymentException("Only failed payments can be retried");
    }

    Payment retryPayment = Payment.builder()
        .user(failedPayment.getUser())
        .status(PaymentStatus.PENDING)
        .gateway(failedPayment.getGateway())
        .category(failedPayment.getCategory())
        .price(failedPayment.getPrice())
        .groupId(failedPayment.getGroupId())
        .moreLoginCount(failedPayment.getMoreLoginCount())
        .renew(failedPayment.isRenew())
        .build();

    paymentRepository.save(retryPayment);
    log.info("Created retry payment {} for failed payment {}", retryPayment.getId(), failedPayment.getId());
    return retryPayment;
  }

  @Transactional
  public void handleCoinPaymentStatus(Payment payment, int status) {
    try {
      if (status >= 100 || status == 2) { // Completed
        payment.setStatus(PaymentStatus.SUCCEEDED);
        fullFillPayment(payment);
      } else if (status < 0) { // Failed
        payment.setStatus(PaymentStatus.FAILED);
        payment.setErrorMessage("CoinPayment transaction failed");
      } else {
        payment.setStatus(PaymentStatus.PENDING);
      }
      paymentRepository.save(payment);
    } catch (Exception e) {
      log.error("Error handling CoinPayment status update", e);
      throw new PaymentException("Failed to process CoinPayment status update", e);
    }
  }
}