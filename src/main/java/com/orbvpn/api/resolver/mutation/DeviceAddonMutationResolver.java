package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.DevicePriceCalculation;
import com.orbvpn.api.domain.dto.PaymentResponse;
import com.orbvpn.api.domain.dto.ResellerDeviceAddResult;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.PaymentCategory;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.domain.payload.CoinPayment.CoinPaymentResponse;
import com.orbvpn.api.exception.PaymentException;
import com.orbvpn.api.repository.PaymentRepository;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.device.DevicePricingService;
import com.orbvpn.api.service.payment.PaypalService;
import com.orbvpn.api.service.payment.StripeService;
import com.orbvpn.api.service.payment.coinpayment.CoinPaymentService;
import com.orbvpn.api.service.reseller.ResellerDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.RESELLER;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

/**
 * GraphQL mutation resolver for device addon operations.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@Transactional
public class DeviceAddonMutationResolver {

    private final DevicePricingService devicePricingService;
    private final ResellerDeviceService resellerDeviceService;
    private final UserService userService;
    private final PaymentRepository paymentRepository;
    private final StripeService stripeService;
    private final PaypalService paypalService;
    private final CoinPaymentService coinPaymentService;

    /**
     * Purchase device addon for current user using standard payment flow.
     *
     * @param deviceCount   Number of devices to add
     * @param paymentMethod Payment method (STRIPE, PAYPAL, COIN_PAYMENT, etc.)
     * @param selectedCoin  Optional coin type for crypto payments
     * @return Payment response with checkout URL or mobile product ID
     */
    @Secured(USER)
    @MutationMapping
    public PaymentResponse purchaseDeviceAddon(
            @Argument @Valid @Min(value = 1, message = "Device count must be at least 1") Integer deviceCount,
            @Argument String paymentMethod,
            @Argument @Nullable String selectedCoin) {

        log.info("Initiating device addon purchase: {} devices, method: {}", deviceCount, paymentMethod);

        try {
            User user = userService.getUser();
            DevicePriceCalculation priceCalc = devicePricingService.calculateUserDevicePrice(deviceCount);

            // Create payment record
            Payment payment = Payment.builder()
                    .user(user)
                    .status(PaymentStatus.PENDING)
                    .gateway(GatewayName.valueOf(paymentMethod.toUpperCase()))
                    .category(PaymentCategory.MORE_LOGIN)
                    .price(priceCalc.getFinalPrice())
                    .moreLoginCount(deviceCount)
                    .build();

            payment = paymentRepository.save(payment);

            // Process based on payment method
            GatewayName gateway = GatewayName.valueOf(paymentMethod.toUpperCase());

            switch (gateway) {
                case STRIPE:
                    return stripeService.createPaymentIntent(payment);

                case PAYPAL:
                    var paypalResponse = paypalService.createPayment(payment);
                    return PaymentResponse.builder()
                            .success(true)
                            .paymentId(paypalResponse.getOrderId())
                            .gatewayReference(paypalResponse.getOrderId())
                            .status("CREATED")
                            .build();

                case COIN_PAYMENT:
                    String coin = (selectedCoin != null && !selectedCoin.isEmpty()) ? selectedCoin : "BTC";
                    CoinPayment coinPayment = CoinPayment.builder()
                            .user(user)
                            .payment(payment)
                            .coin(coin)
                            .coinAmount(priceCalc.getFinalPrice().toString())
                            .status(false)
                            .build();

                    CoinPaymentResponse coinResponse = coinPaymentService.createPayment(coinPayment);
                    return PaymentResponse.builder()
                            .success(coinResponse.getError() == null || coinResponse.getError().isEmpty())
                            .paymentId(String.valueOf(coinResponse.getId()))
                            .checkoutUrl(coinResponse.getCheckout_url())
                            .qrcodeUrl(coinResponse.getQrcode_url())
                            .amount(Double.parseDouble(coinResponse.getAmount()))
                            .currency(coin)
                            .gatewayReference(coinResponse.getTxn_id())
                            .status("PENDING")
                            .message(coinResponse.getError())
                            .build();

                case APPLE_STORE:
                case GOOGLE_PLAY:
                    // For mobile IAP, return product ID for client-side purchase
                    return PaymentResponse.builder()
                            .success(true)
                            .mobileProductId("device_addon_" + deviceCount)
                            .amount(priceCalc.getFinalPrice().doubleValue())
                            .message("Use the mobile product ID to complete purchase in-app")
                            .build();

                default:
                    throw new PaymentException("Unsupported payment method: " + paymentMethod);
            }

        } catch (Exception e) {
            log.error("Error initiating device addon purchase: {}", e.getMessage(), e);
            return PaymentResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build();
        }
    }

    /**
     * Reseller adds devices to a user's subscription.
     * Charges the reseller's credit based on pro-rata pricing.
     *
     * @param userId      The user ID
     * @param deviceCount Number of devices to add
     * @return Result of the operation
     */
    @Secured({ADMIN, RESELLER})
    @MutationMapping
    public ResellerDeviceAddResult resellerAddDevices(
            @Argument @Valid @Min(value = 1, message = "User ID must be positive") Integer userId,
            @Argument @Valid @Min(value = 1, message = "Device count must be at least 1") Integer deviceCount) {

        log.info("Reseller adding {} devices to user {}", deviceCount, userId);

        try {
            User accessor = userService.getUser();
            Reseller reseller = accessor.getReseller();

            if (reseller == null && !userService.isAdmin()) {
                return ResellerDeviceAddResult.builder()
                        .success(false)
                        .userId(userId)
                        .message("Only resellers or admins can add devices")
                        .errorCode("NOT_AUTHORIZED")
                        .build();
            }

            int resellerId = reseller != null ? reseller.getId() : getResellerIdForUser(userId);

            return resellerDeviceService.addDevicesToUser(resellerId, userId, deviceCount);

        } catch (Exception e) {
            log.error("Error adding devices for user {}: {}", userId, e.getMessage(), e);
            return ResellerDeviceAddResult.builder()
                    .success(false)
                    .userId(userId)
                    .message(e.getMessage())
                    .errorCode("ERROR")
                    .build();
        }
    }

    /**
     * Reseller sets exact device count for a user.
     * Only charges for increases, no refund for decreases.
     *
     * @param userId      The user ID
     * @param deviceCount New total device count
     * @return Result of the operation
     */
    @Secured({ADMIN, RESELLER})
    @MutationMapping
    public ResellerDeviceAddResult resellerSetDeviceCount(
            @Argument @Valid @Min(value = 1, message = "User ID must be positive") Integer userId,
            @Argument @Valid @Min(value = 1, message = "Device count must be at least 1") Integer deviceCount) {

        log.info("Reseller setting device count to {} for user {}", deviceCount, userId);

        try {
            User accessor = userService.getUser();
            Reseller reseller = accessor.getReseller();

            if (reseller == null && !userService.isAdmin()) {
                return ResellerDeviceAddResult.builder()
                        .success(false)
                        .userId(userId)
                        .message("Only resellers or admins can set device count")
                        .errorCode("NOT_AUTHORIZED")
                        .build();
            }

            int resellerId = reseller != null ? reseller.getId() : getResellerIdForUser(userId);

            return resellerDeviceService.setUserDeviceCount(resellerId, userId, deviceCount);

        } catch (Exception e) {
            log.error("Error setting device count for user {}: {}", userId, e.getMessage(), e);
            return ResellerDeviceAddResult.builder()
                    .success(false)
                    .userId(userId)
                    .message(e.getMessage())
                    .errorCode("ERROR")
                    .build();
        }
    }

    /**
     * Get reseller ID for a user (when admin is accessing).
     */
    private int getResellerIdForUser(int userId) {
        User user = userService.getUserById(userId);
        if (user.getReseller() != null) {
            return user.getReseller().getId();
        }
        throw new IllegalStateException("User has no reseller assigned");
    }
}
