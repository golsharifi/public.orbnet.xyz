package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.AccountingView;
import com.orbvpn.api.domain.dto.PriceView;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.service.AccountingService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AccountingQueryResolver {
  private final AccountingService accountingService;

  @Secured(ADMIN)
  @QueryMapping
  public AccountingView accounting() {
    log.info("Fetching accounting information");
    try {
      AccountingView accounting = accountingService.getAccounting();
      log.info("Successfully retrieved accounting information");
      return accounting;
    } catch (Exception e) {
      log.error("Error fetching accounting information - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @QueryMapping
  public PriceView calculateDiscountedPrice(@Argument @Valid Group group) {
    log.info("Calculating discounted price for group: {}", group.getId());
    try {
      PriceView price = accountingService.calculateDiscountedPriceByDuration(group);
      log.info("Successfully calculated discounted price for group: {}", group.getId());
      return price;
    } catch (Exception e) {
      log.error("Error calculating discounted price for group: {} - Error: {}",
          group.getId(), e.getMessage(), e);
      throw e;
    }
  }
}