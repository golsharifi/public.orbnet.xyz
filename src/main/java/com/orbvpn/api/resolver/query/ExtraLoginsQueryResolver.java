package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.ExtraLoginsPlanView;
import com.orbvpn.api.domain.dto.UserExtraLoginsView;
import com.orbvpn.api.domain.dto.PriceCalculation;
import com.orbvpn.api.service.ExtraLoginsService;
import com.orbvpn.api.exception.NotFoundException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ExtraLoginsQueryResolver {
    private final ExtraLoginsService extraLoginsService;

    @Secured(USER)
    @QueryMapping
    public List<ExtraLoginsPlanView> extraLoginPlans(
            @Argument @Valid @NotBlank(message = "Plan type cannot be empty") String type) {
        log.info("Fetching extra login plans for type: {}", type);
        try {
            List<ExtraLoginsPlanView> plans = extraLoginsService.getAvailablePlans(type);
            log.info("Successfully retrieved {} extra login plans", plans.size());
            return plans;
        } catch (Exception e) {
            log.error("Error fetching extra login plans - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public ExtraLoginsPlanView extraLoginPlan(
            @Argument @Valid @Positive(message = "Plan ID must be positive") Long id) {
        log.info("Fetching extra login plan with id: {}", id);
        try {
            ExtraLoginsPlanView plan = extraLoginsService.getPlan(id);
            if (plan == null) {
                throw new NotFoundException("Extra login plan not found with id: " + id);
            }
            log.info("Successfully retrieved extra login plan: {}", id);
            return plan;
        } catch (NotFoundException e) {
            log.warn("Extra login plan not found - ID: {} - Error: {}", id, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error fetching extra login plan: {} - Error: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public List<UserExtraLoginsView> userExtraLogins() {
        log.info("Fetching user extra logins");
        try {
            List<UserExtraLoginsView> extraLogins = extraLoginsService.getUserExtraLogins();
            log.info("Successfully retrieved {} user extra logins", extraLogins.size());
            return extraLogins;
        } catch (Exception e) {
            log.error("Error fetching user extra logins - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public PriceCalculation calculateExtraLoginPrice(
            @Argument @Valid @Positive(message = "Plan ID must be positive") Long planId,
            @Argument @Valid @Positive(message = "Quantity must be positive") Integer quantity) {
        log.info("Calculating price for plan: {} with quantity: {}", planId, quantity);
        try {
            PriceCalculation calculation = extraLoginsService.calculatePrice(planId, quantity);
            log.info("Successfully calculated price for plan: {} with quantity: {}", planId, quantity);
            return calculation;
        } catch (Exception e) {
            log.error("Error calculating price for plan: {} - Error: {}", planId, e.getMessage(), e);
            throw e;
        }
    }
}