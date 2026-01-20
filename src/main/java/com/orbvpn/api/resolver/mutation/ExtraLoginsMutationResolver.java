package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.ExtraLoginsPlanView;
import com.orbvpn.api.domain.dto.ExtraLoginsPlanEdit;
import com.orbvpn.api.domain.dto.PaymentResponse;
import com.orbvpn.api.service.ExtraLoginsService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ExtraLoginsMutationResolver {
    private final ExtraLoginsService extraLoginsService;

    @Secured({ USER, ADMIN })
    @MutationMapping
    public PaymentResponse purchaseExtraLogins(
            @Argument @Valid @Positive(message = "Plan ID must be positive") Long planId,
            @Argument @Valid @Min(value = 1, message = "Quantity must be at least 1") int quantity,
            @Argument @Valid @NotBlank(message = "Payment method is required") String paymentMethod,
            @Argument String selectedCoin) {
        log.info("Processing extra logins purchase - Plan: {}, Quantity: {}", planId, quantity);
        try {
            return extraLoginsService.initiatePayment(planId, quantity, paymentMethod, selectedCoin);
        } catch (Exception e) {
            log.error("Error processing extra logins purchase - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ USER, ADMIN })
    @MutationMapping
    public Boolean giftExtraLogins(
            @Argument @Valid @Positive(message = "Plan ID must be positive") Long planId,
            @Argument @Valid @NotBlank(message = "Recipient email is required") String recipientEmail) {
        log.info("Processing extra logins gift for recipient: {}", recipientEmail);
        try {
            extraLoginsService.giftExtraLogins(planId, recipientEmail);
            return true;
        } catch (Exception e) {
            log.error("Error processing extra logins gift - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ USER, ADMIN })
    @MutationMapping
    public Boolean cancelExtraLoginSubscription(
            @Argument @Valid @NotBlank(message = "Subscription ID is required") String subscriptionId) {
        log.info("Cancelling extra logins subscription: {}", subscriptionId);
        try {
            extraLoginsService.cancelSubscription(subscriptionId);
            return true;
        } catch (Exception e) {
            log.error("Error cancelling subscription: {} - Error: {}", subscriptionId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public ExtraLoginsPlanView createExtraLoginPlan(
            @Argument @Valid ExtraLoginsPlanEdit input) {
        log.info("Creating new extra logins plan");
        try {
            return extraLoginsService.createPlan(input);
        } catch (Exception e) {
            log.error("Error creating extra logins plan - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public ExtraLoginsPlanView updateExtraLoginPlan(
            @Argument @Valid @Positive(message = "Plan ID must be positive") Long id,
            @Argument @Valid ExtraLoginsPlanEdit input) {
        log.info("Updating extra logins plan: {}", id);
        try {
            return extraLoginsService.updatePlan(id, input);
        } catch (Exception e) {
            log.error("Error updating extra logins plan: {} - Error: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean deleteExtraLoginPlan(
            @Argument @Valid @Positive(message = "Plan ID must be positive") Long id) {
        log.info("Deleting extra logins plan: {}", id);
        try {
            extraLoginsService.deletePlan(id);
            return true;
        } catch (Exception e) {
            log.error("Error deleting extra logins plan: {} - Error: {}", id, e.getMessage(), e);
            throw e;
        }
    }
}