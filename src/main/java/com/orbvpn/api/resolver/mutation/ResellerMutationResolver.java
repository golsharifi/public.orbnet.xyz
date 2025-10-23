package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.ResellerCreate;
import com.orbvpn.api.domain.dto.ResellerEdit;
import com.orbvpn.api.domain.dto.ResellerView;
import com.orbvpn.api.domain.dto.ResellerLevelEdit;
import com.orbvpn.api.domain.dto.ResellerLevelView;
import com.orbvpn.api.domain.dto.ResellerLevelCoefficientsEdit;
import com.orbvpn.api.domain.dto.ResellerLevelCoefficientsView;
import com.orbvpn.api.domain.dto.ResellerScoreLimitEdit;
import com.orbvpn.api.domain.dto.ResellerScoreDto;
import com.orbvpn.api.domain.entity.ResellerScoreLimit;
import com.orbvpn.api.domain.enums.ResellerLevelName;
import com.orbvpn.api.service.reseller.ResellerScoreService;
import com.orbvpn.api.service.reseller.ResellerService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ResellerMutationResolver {

    private final ResellerService resellerService;
    private final ResellerScoreService resellerScoreService;

    @Secured(ADMIN)
    @MutationMapping
    public ResellerView createReseller(@Argument @Valid ResellerCreate reseller) {
        log.info("Creating new reseller");
        try {
            return resellerService.createReseller(reseller);
        } catch (Exception e) {
            log.error("Error creating reseller - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public ResellerView editReseller(
            @Argument @Valid @Min(1) int id,
            @Argument @Valid ResellerEdit reseller) {
        log.info("Editing reseller: {}", id);
        try {
            return resellerService.editReseller(id, reseller);
        } catch (Exception e) {
            log.error("Error editing reseller: {} - Error: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public ResellerView setResellerLevel(
            @Argument @Valid @Min(1) int resellerId,
            @Argument @Valid @NotNull ResellerLevelName level) {
        log.info("Setting reseller level - id: {}, level: {}", resellerId, level);
        try {
            return resellerService.setResellerLevel(resellerId, level);
        } catch (Exception e) {
            log.error("Error setting reseller level - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public ResellerView addResellerCredit(
            @Argument @Valid @Min(1) int resellerId,
            @Argument @Valid @DecimalMin(value = "0.0", inclusive = false) BigDecimal credit) {
        log.info("Adding credit to reseller - id: {}, amount: {}", resellerId, credit);
        try {
            return resellerService.addResellerCredit(resellerId, credit);
        } catch (Exception e) {
            log.error("Error adding reseller credit - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public ResellerView deductResellerCredit(
            @Argument @Valid @Min(1) int resellerId,
            @Argument @Valid @DecimalMin(value = "0.0", inclusive = false) BigDecimal credit) {
        log.info("Deducting credit from reseller - id: {}, amount: {}", resellerId, credit);
        try {
            return resellerService.deductResellerCredit(resellerId, credit);
        } catch (Exception e) {
            log.error("Error deducting reseller credit - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public ResellerView addResellerServiceGroup(
            @Argument @Valid @Min(1) int resellerId,
            @Argument @Valid @Min(1) int serviceGroupId) {
        log.info("Adding service group to reseller - resellerId: {}, serviceGroupId: {}", resellerId, serviceGroupId);
        try {
            return resellerService.addResellerServiceGroup(resellerId, serviceGroupId);
        } catch (Exception e) {
            log.error("Error adding service group to reseller - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public ResellerView removeResellerServiceGroup(
            @Argument @Valid @Min(1) int resellerId,
            @Argument @Valid @Min(1) int serviceGroupId) {
        log.info("Removing service group from reseller - resellerId: {}, serviceGroupId: {}", resellerId,
                serviceGroupId);
        try {
            return resellerService.removeResellerServiceGroup(resellerId, serviceGroupId);
        } catch (Exception e) {
            log.error("Error removing service group from reseller - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public ResellerView deleteReseller(@Argument @Valid @Min(1) int id) {
        log.info("Deleting reseller: {}", id);
        try {
            return resellerService.deleteReseller(id);
        } catch (Exception e) {
            log.error("Error deleting reseller - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public ResellerLevelView updateResellerLevel(
            @Argument @Valid @Min(1) int id,
            @Argument @Valid ResellerLevelEdit level) {
        log.info("Updating reseller level: {}", id);
        try {
            return resellerService.updateResellerLevel(id, level);
        } catch (Exception e) {
            log.error("Error updating reseller level - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public ResellerLevelCoefficientsView updateResellerLevelCoefficients(
            @Argument @Valid ResellerLevelCoefficientsEdit coefficients) {
        log.info("Updating reseller level coefficients");
        try {
            return resellerService.updateResellerLevelCoefficients(coefficients);
        } catch (Exception e) {
            log.error("Error updating reseller level coefficients - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public ResellerScoreLimit updateResellerScoreLimitBySymbol(
            @Argument @Valid ResellerScoreLimitEdit scoreLimitEdit) {
        log.info("Updating reseller score limit by symbol");
        try {
            return resellerScoreService.updateScoreBySymbol(scoreLimitEdit);
        } catch (Exception e) {
            log.error("Error updating reseller score limit - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public List<ResellerScoreLimit> updateResellerScoreLimits(
            @Argument List<@Valid ResellerScoreLimitEdit> scoreLimitEdits) {
        log.info("Updating multiple reseller score limits");
        try {
            return resellerScoreService.updateResellerScoreLimits(scoreLimitEdits);
        } catch (Exception e) {
            log.error("Error updating reseller score limits - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public ResellerScoreDto calculateResellerScore(@Argument @Valid @Min(1) int resellerId) {
        log.info("Calculating score for reseller: {}", resellerId);
        try {
            return resellerScoreService.calculateResellerScore(resellerId);
        } catch (Exception e) {
            log.error("Error calculating reseller score - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}