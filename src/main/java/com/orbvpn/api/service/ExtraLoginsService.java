package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.PaymentCategory;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.domain.payload.CoinPayment.CoinPaymentResponse;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.exception.PaymentException;
import com.orbvpn.api.mapper.ExtraLoginsPlanMapper;
import com.orbvpn.api.mapper.UserExtraLoginsMapper;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.service.payment.*;
import com.orbvpn.api.service.payment.coinpayment.CoinPaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Validated
@Transactional
public class ExtraLoginsService {
    private final ExtraLoginsPlanRepository planRepository;
    private final UserExtraLoginsRepository userExtraLoginsRepository;
    private final LoyaltyProgramRepository loyaltyRepository;
    private final PaymentRepository paymentRepository;
    private final UserService userService;
    private final AsyncNotificationHelper asyncNotificationHelper;
    private final ExtraLoginsPlanMapper extraLoginsPlanMapper;
    private final UserExtraLoginsMapper userExtraLoginsMapper;
    private final StripeService stripeService;
    private final PaypalService paypalService;
    private final CoinPaymentService coinPaymentService;

    @Transactional(readOnly = true)
    public ExtraLoginsPlanView getPlan(Long id) {
        ExtraLoginsPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Extra logins plan not found with id: " + id));
        return extraLoginsPlanMapper.toView(plan);
    }

    @Transactional(readOnly = true)
    public List<ExtraLoginsPlanView> getAvailablePlans(String type) {
        List<ExtraLoginsPlan> plans;
        if ("subscription".equalsIgnoreCase(type)) {
            plans = planRepository.findBySubscriptionAndActive(true, true);
        } else if ("gift".equalsIgnoreCase(type)) {
            plans = planRepository.findByGiftableAndActive(true, true);
        } else if ("temporary".equalsIgnoreCase(type)) {
            plans = planRepository.findTemporaryPlans();
        } else {
            plans = planRepository.findAll();
        }
        return plans.stream()
                .map(extraLoginsPlanMapper::toView)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PriceCalculation calculatePrice(@NotNull Long planId, @Min(1) int quantity) {
        ExtraLoginsPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("Extra logins plan not found with id: " + planId));

        User user = userService.getUser();
        BigDecimal basePrice = plan.getBasePrice();
        BigDecimal bulkDiscount = BigDecimal.ZERO;
        BigDecimal loyaltyDiscount = BigDecimal.ZERO;

        // Calculate base total before discounts
        BigDecimal priceBeforeDiscounts = basePrice.multiply(BigDecimal.valueOf(quantity));

        // Apply bulk discount if applicable
        if (quantity >= plan.getMinimumQuantity()) {
            bulkDiscount = plan.getBulkDiscountPercent();
            BigDecimal bulkDiscountAmount = priceBeforeDiscounts.multiply(
                    bulkDiscount.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
            priceBeforeDiscounts = priceBeforeDiscounts.subtract(bulkDiscountAmount);
            log.debug("Applied bulk discount for quantity {}: {}", quantity, bulkDiscount);
        }

        // Apply loyalty discount if eligible
        int accountAgeDays = Period.between(user.getCreatedAt().toLocalDate(),
                LocalDateTime.now().toLocalDate()).getDays();

        Optional<LoyaltyProgram> loyaltyProgram = loyaltyRepository
                .findByMinAccountAgeDaysLessThanEqual(accountAgeDays)
                .stream()
                .max(Comparator.comparing(LoyaltyProgram::getMinAccountAgeDays));

        if (loyaltyProgram.isPresent()) {
            loyaltyDiscount = loyaltyProgram.get().getDiscountPercent();
            BigDecimal loyaltyDiscountAmount = priceBeforeDiscounts.multiply(
                    loyaltyDiscount.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
            priceBeforeDiscounts = priceBeforeDiscounts.subtract(loyaltyDiscountAmount);
            log.debug("Applied loyalty discount for user {}: {}", user.getId(), loyaltyDiscount);
        }

        return PriceCalculation.builder()
                .basePrice(basePrice)
                .bulkDiscount(bulkDiscount)
                .loyaltyDiscount(loyaltyDiscount)
                .finalPrice(priceBeforeDiscounts.setScale(2, RoundingMode.HALF_UP))
                .currency("USD")
                .build();
    }

    @Transactional
    public PaymentResponse initiatePayment(@Valid @NotNull Long planId, @Min(1) int quantity,
            @NotNull String paymentMethod, @Nullable String selectedCoin) {
        ExtraLoginsPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("Extra logins plan not found with id: " + planId));

        PriceCalculation priceCalc = calculatePrice(planId, quantity);
        User user = userService.getUser();

        Payment payment = Payment.builder()
                .user(user)
                .status(PaymentStatus.PENDING)
                .gateway(GatewayName.valueOf(paymentMethod))
                .category(PaymentCategory.MORE_LOGIN)
                .price(priceCalc.getFinalPrice())
                .moreLoginCount(plan.getLoginCount() * quantity)
                .groupId(plan.getId().intValue())
                .build();

        if (plan.isSubscription()) {
            payment.setRenew(true);
        }

        payment = paymentRepository.save(payment);

        try {
            switch (GatewayName.valueOf(paymentMethod.toUpperCase())) {
                case STRIPE:
                    return stripeService.createPaymentIntent(payment);

                case PAYPAL:
                    PaypalCreatePaymentResponse paypalResponse = paypalService.createPayment(payment);
                    return convertPaypalResponseToPaymentResponse(paypalResponse);

                case COIN_PAYMENT:
                    if (selectedCoin == null || selectedCoin.isEmpty()) {
                        selectedCoin = "BTC"; // Default to BTC if no coin is selected
                    }
                    CoinPayment coinPayment = convertPaymentToCoinPayment(payment, selectedCoin);
                    CoinPaymentResponse coinPaymentResponse = coinPaymentService.createPayment(coinPayment);
                    return convertCoinPaymentResponseToPaymentResponse(coinPaymentResponse, selectedCoin);

                case APPLE_STORE:
                case GOOGLE_PLAY:
                    return PaymentResponse.builder()
                            .success(true)
                            .mobileProductId(plan.getMobileProductId())
                            .amount(priceCalc.getFinalPrice().doubleValue())
                            .build();

                default:
                    throw new PaymentException("Unsupported payment method: " + paymentMethod);
            }
        } catch (Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorMessage(e.getMessage());
            paymentRepository.save(payment);

            return PaymentResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build();
        }
    }

    private PaymentResponse convertPaypalResponseToPaymentResponse(PaypalCreatePaymentResponse paypalResponse) {
        return PaymentResponse.builder()
                .success(true)
                .paymentId(paypalResponse.getOrderId())
                .checkoutUrl(null) // We don't have this information in the response
                .status("CREATED") // Assuming the order is created successfully
                .amount(null) // We don't have this information in the response
                .currency(null) // We don't have this information in the response
                .gatewayReference(paypalResponse.getOrderId())
                .build();
    }

    private CoinPayment convertPaymentToCoinPayment(Payment payment, String selectedCoin) {
        return CoinPayment.builder()
                .user(payment.getUser())
                .payment(payment)
                .coin(selectedCoin) // Use the selected coin instead of hardcoding "BTC"
                .coinAmount(payment.getPrice().toString())
                .status(false) // Assuming false is the initial status
                .build();
    }

    private PaymentResponse convertCoinPaymentResponseToPaymentResponse(CoinPaymentResponse coinPaymentResponse,
            String selectedCoin) {
        return PaymentResponse.builder()
                .success(coinPaymentResponse.getError() == null || coinPaymentResponse.getError().isEmpty())
                .paymentId(String.valueOf(coinPaymentResponse.getId()))
                .checkoutUrl(coinPaymentResponse.getCheckout_url())
                .status("PENDING") // Since there's no status field, we'll default to "PENDING"
                .amount(Double.parseDouble(coinPaymentResponse.getAmount()))
                .currency(selectedCoin) // Use the selected coin as the currency
                .gatewayReference(coinPaymentResponse.getTxn_id())
                .message(coinPaymentResponse.getError())
                .requiresAction(false)
                .qrcodeUrl(coinPaymentResponse.getQrcode_url()) // This should now work
                .build();
    }

    @Transactional
    public void handlePaymentSuccess(Payment payment) {
        log.info("Processing successful payment {}", payment.getId());

        ExtraLoginsPlan plan = planRepository.findById(Long.valueOf(payment.getGroupId()))
                .orElseThrow(() -> new NotFoundException("Plan not found"));

        User user = payment.getUser();

        UserExtraLogins extraLogins = new UserExtraLogins();
        extraLogins.setUser(user);
        extraLogins.setPlan(plan);
        extraLogins.setLoginCount(payment.getMoreLoginCount());
        extraLogins.setActive(true);
        extraLogins.setStartDate(LocalDateTime.now());

        if (plan.getDurationDays() > 0) {
            extraLogins.setExpiryDate(LocalDateTime.now().plusDays(plan.getDurationDays()));
        }

        if (plan.isSubscription()) {
            extraLogins.setSubscription(true);
            extraLogins.setSubscriptionId(payment.getSubscriptionId());
        }

        userExtraLoginsRepository.save(extraLogins);
        // Update user's total login count
        int newLoginCount = userExtraLoginsRepository.getTotalActiveLoginCount(user);
        userService.updateUserLoginCount(user, newLoginCount);

        asyncNotificationHelper.sendExtraLoginsConfirmationAsync(user, extraLogins);

        log.info("Successfully processed payment {} for user {}", payment.getId(), user.getId());
    }

    @Transactional
    public void giftExtraLogins(@NotNull Long planId, @NotNull String recipientEmail) {
        ExtraLoginsPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("Plan not found"));

        if (!plan.isGiftable()) {
            throw new IllegalArgumentException("This plan cannot be gifted");
        }

        User sender = userService.getUser();
        User recipient = userService.getUserByEmail(recipientEmail);

        UserExtraLogins gift = new UserExtraLogins();
        gift.setUser(recipient);
        gift.setPlan(plan);
        gift.setGiftedBy(sender);
        gift.setLoginCount(1);
        gift.setActive(true);
        gift.setStartDate(LocalDateTime.now());

        if (plan.getDurationDays() > 0) {
            gift.setExpiryDate(LocalDateTime.now().plusDays(plan.getDurationDays()));
        }

        userExtraLoginsRepository.save(gift);

        // Update recipient's total login count
        int newLoginCount = userExtraLoginsRepository.getTotalActiveLoginCount(recipient);
        userService.updateUserLoginCount(recipient, newLoginCount);

        asyncNotificationHelper.sendExtraLoginsGiftNotificationAsync(sender, recipient, gift);

        log.info("User {} gifted extra logins to user {}", sender.getId(), recipient.getId());
    }

    @Transactional(readOnly = true)
    public List<UserExtraLoginsView> getUserExtraLogins() {
        User user = userService.getUser();
        List<UserExtraLogins> extraLogins = userExtraLoginsRepository.findByUserAndActiveTrue(user);
        return extraLogins.stream()
                .map(userExtraLoginsMapper::toView)
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelSubscription(String subscriptionId) {
        UserExtraLogins subscription = userExtraLoginsRepository.findBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new NotFoundException("Subscription not found"));

        if (!subscription.getUser().equals(userService.getUser())) {
            throw new IllegalArgumentException("Not authorized to cancel this subscription");
        }

        subscription.setActive(false);
        userExtraLoginsRepository.save(subscription);

        User user = subscription.getUser();
        int newLoginCount = userExtraLoginsRepository.getTotalActiveLoginCount(user);
        userService.updateUserLoginCount(user, newLoginCount);

        log.info("Cancelled subscription {} for user {}", subscriptionId, user.getId());
    }

    @Transactional
    public ExtraLoginsPlanView createPlan(@Valid ExtraLoginsPlanEdit input) {
        ExtraLoginsPlan plan = extraLoginsPlanMapper.create(input);
        planRepository.save(plan);
        log.info("Created new extra logins plan: {}", plan.getName());
        return extraLoginsPlanMapper.toView(plan);
    }

    @Transactional
    public ExtraLoginsPlanView updatePlan(Long id, @Valid ExtraLoginsPlanEdit input) {
        ExtraLoginsPlan existingPlan = planRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Extra logins plan not found with id: " + id));

        extraLoginsPlanMapper.update(existingPlan, input);
        planRepository.save(existingPlan);
        log.info("Updated extra logins plan: {}", existingPlan.getName());
        return extraLoginsPlanMapper.toView(existingPlan);
    }

    @Transactional
    public void deletePlan(Long id) {
        ExtraLoginsPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Extra logins plan not found with id: " + id));

        boolean hasActiveSubscriptions = userExtraLoginsRepository
                .existsByPlanAndActiveTrue(plan);

        if (hasActiveSubscriptions) {
            throw new IllegalStateException("Cannot delete plan with active subscriptions");
        }

        plan.setActive(false);
        planRepository.save(plan);
        log.info("Deleted extra logins plan: {}", plan.getName());
    }
}