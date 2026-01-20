package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.Reseller;
import com.orbvpn.api.domain.entity.ResellerScoreLimit;
import com.orbvpn.api.service.reseller.ResellerLevelService;
import com.orbvpn.api.service.reseller.ResellerLevelService.ScoreBreakdown;
import com.orbvpn.api.service.reseller.ResellerScoreService;
import com.orbvpn.api.service.reseller.ResellerService;
import com.orbvpn.api.exception.NotFoundException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ResellerQueryResolver {
  private final ResellerService resellerService;
  private final ResellerScoreService resellerScoreService;
  private final ResellerLevelService resellerLevelService;

  @Secured(ADMIN)
  @QueryMapping
  public ResellerView reseller(@Argument @Valid @Positive(message = "ID must be positive") Integer id) {
    log.info("Fetching reseller with id: {}", id);
    try {
      ResellerView reseller = resellerService.getReseller(id);
      if (reseller == null) {
        throw new NotFoundException("Reseller not found with id: " + id);
      }
      log.info("Successfully retrieved reseller: {}", id);
      return reseller;
    } catch (NotFoundException e) {
      log.warn("Reseller not found - ID: {} - Error: {}", id, e.getMessage());
      throw e;
    } catch (Exception e) {
      log.error("Error fetching reseller: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @QueryMapping
  public List<ResellerView> resellers() {
    log.info("Fetching enabled resellers");
    try {
      List<ResellerView> resellers = resellerService.getEnabledResellers();
      log.info("Successfully retrieved {} enabled resellers", resellers.size());
      return resellers;
    } catch (Exception e) {
      log.error("Error fetching enabled resellers - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @QueryMapping
  public BigDecimal totalResellersCredit() {
    log.info("Calculating total resellers credit");
    try {
      BigDecimal total = resellerService.getTotalResellersCredit();
      log.info("Successfully calculated total resellers credit: {}", total);
      return total;
    } catch (Exception e) {
      log.error("Error calculating total resellers credit - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @QueryMapping
  public List<ResellerLevelView> getResellersLevels() {
    log.info("Fetching reseller levels");
    try {
      List<ResellerLevelView> levels = resellerService.getResellersLevels();
      log.info("Successfully retrieved {} reseller levels", levels.size());
      return levels;
    } catch (Exception e) {
      log.error("Error fetching reseller levels - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @QueryMapping
  public List<ResellerCreditView> getResellersCredits() {
    log.info("Fetching reseller credits");
    try {
      List<ResellerCreditView> credits = resellerService.getResellersCredits();
      log.info("Successfully retrieved {} reseller credits", credits.size());
      return credits;
    } catch (Exception e) {
      log.error("Error fetching reseller credits - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @QueryMapping
  public ResellerLevelCoefficientsView getResellerLevelCoefficients() {
    log.info("Fetching reseller level coefficients");
    try {
      ResellerLevelCoefficientsView coefficients = resellerService.getResellerLevelCoefficients();
      log.info("Successfully retrieved reseller level coefficients");
      return coefficients;
    } catch (Exception e) {
      log.error("Error fetching reseller level coefficients - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @QueryMapping
  public List<ResellerScoreLimit> getResellerScoreLimits() {
    log.info("Fetching reseller score limits");
    try {
      List<ResellerScoreLimit> limits = resellerScoreService.getScoreLimits();
      log.info("Successfully retrieved {} reseller score limits", limits.size());
      return limits;
    } catch (Exception e) {
      log.error("Error fetching reseller score limits - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @QueryMapping
  public ResellerScoreBreakdownView getResellerScoreBreakdown(
          @Argument @Valid @Positive(message = "ID must be positive") Integer resellerId) {
    log.info("Fetching score breakdown for reseller: {}", resellerId);
    try {
      Reseller reseller = resellerService.getResellerById(resellerId);
      ScoreBreakdown breakdown = resellerLevelService.calculateScoreBreakdown(reseller);

      // Calculate recommended level and points to next level
      String recommendedLevel = breakdown.getCurrentLevel();
      BigDecimal nextLevelScore = null;
      BigDecimal pointsToNextLevel = null;

      if (!breakdown.isOwner()) {
        // Get all levels to find next tier
        java.util.List<ResellerLevelView> levels = resellerService.getResellersLevels();
        levels.sort((a, b) -> a.getMinScore().compareTo(b.getMinScore()));

        for (ResellerLevelView level : levels) {
          BigDecimal levelMinScore = level.getMinScore();
          if (breakdown.getTotalScore().compareTo(levelMinScore) >= 0) {
            recommendedLevel = level.getName().name();
          } else if (nextLevelScore == null) {
            nextLevelScore = levelMinScore;
            pointsToNextLevel = levelMinScore.subtract(breakdown.getTotalScore());
            break;
          }
        }
      }

      ResellerScoreBreakdownView view = ResellerScoreBreakdownView.fromBreakdown(
              breakdown, recommendedLevel, nextLevelScore, pointsToNextLevel);

      log.info("Successfully retrieved score breakdown for reseller: {} - Total Score: {}",
              resellerId, breakdown.getTotalScore());
      return view;
    } catch (NotFoundException e) {
      log.warn("Reseller not found for score breakdown - ID: {} - Error: {}", resellerId, e.getMessage());
      throw e;
    } catch (Exception e) {
      log.error("Error fetching score breakdown for reseller: {} - Error: {}", resellerId, e.getMessage(), e);
      throw e;
    }
  }
}