package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.dto.StripeSubscriptionData;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.payment.StripeService;
import com.stripe.exception.StripeException;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PaymentQueryResolver {
    private final StripeService stripeService;
    private final UserService userService;

    @Secured(USER)
    @QueryMapping
    public List<StripeSubscriptionData> getUserStripeSubscriptions() {
        log.info("Fetching user stripe subscriptions");
        try {
            User user = userService.getUser();
            List<StripeSubscriptionData> subscriptions = stripeService.getUserSubscriptions(user);
            log.info("Successfully retrieved stripe subscriptions for user: {}", user.getId());
            return subscriptions;
        } catch (StripeException e) {
            log.error("Error fetching stripe subscriptions - Error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch subscriptions: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error fetching stripe subscriptions - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}