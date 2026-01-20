package com.orbvpn.api.resolver.query;

import com.orbvpn.api.config.security.Unsecured;
import com.orbvpn.api.service.AccountingService;
import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.repository.GroupRepository;
import com.orbvpn.api.exception.NotFoundException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PackagePriceQueryResolver {
  private final AccountingService accountingService;
  private final GroupRepository groupRepository;

  @Unsecured
  @QueryMapping
  public PackagePrice getPackagePrice(
      @Argument @Valid @Positive(message = "Group ID must be positive") Integer groupId) {
    log.info("Fetching package price for group: {}", groupId);
    try {
      PackagePrice price = accountingService.getPackagePrice(groupId);
      log.info("Successfully retrieved package price for group: {}", groupId);
      return price;
    } catch (Exception e) {
      log.error("Error fetching package price for group: {} - Error: {}",
          groupId, e.getMessage(), e);
      throw e;
    }
  }

  @Unsecured
  @QueryMapping
  public PriceView calculateDiscountedPriceByDuration(
      @Argument @Valid @Positive(message = "Group ID must be positive") Integer groupId) {
    log.info("Calculating discounted price for group: {}", groupId);
    try {
      Group group = groupRepository.findById(groupId)
          .orElseThrow(() -> new NotFoundException("Group not found with id: " + groupId));
      PriceView price = accountingService.calculateDiscountedPriceByDuration(group);
      log.info("Successfully calculated discounted price for group: {}", groupId);
      return price;
    } catch (NotFoundException e) {
      log.warn("Group not found - ID: {} - Error: {}", groupId, e.getMessage());
      throw e;
    } catch (Exception e) {
      log.error("Error calculating discounted price for group: {} - Error: {}",
          groupId, e.getMessage(), e);
      throw e;
    }
  }

  @Unsecured
  @QueryMapping
  public PackagePrice3 getPackagePrice3(
      @Argument @Valid @Positive(message = "Group ID must be positive") Integer groupId) {
    log.info("Fetching 3-month package price for group: {}", groupId);
    try {
      PackagePrice3 price = accountingService.getPackagePrice3(groupId);
      log.info("Successfully retrieved 3-month package price for group: {}", groupId);
      return price;
    } catch (Exception e) {
      log.error("Error fetching 3-month package price for group: {} - Error: {}",
          groupId, e.getMessage(), e);
      throw e;
    }
  }

  @Unsecured
  @QueryMapping
  public PackagePrice6 getPackagePrice6(
      @Argument @Valid @Positive(message = "Group ID must be positive") Integer groupId) {
    log.info("Fetching 6-month package price for group: {}", groupId);
    try {
      PackagePrice6 price = accountingService.getPackagePrice6(groupId);
      log.info("Successfully retrieved 6-month package price for group: {}", groupId);
      return price;
    } catch (Exception e) {
      log.error("Error fetching 6-month package price for group: {} - Error: {}",
          groupId, e.getMessage(), e);
      throw e;
    }
  }

  @Unsecured
  @QueryMapping
  public PackagePrice12 getPackagePrice12(
      @Argument @Valid @Positive(message = "Group ID must be positive") Integer groupId) {
    log.info("Fetching 12-month package price for group: {}", groupId);
    try {
      PackagePrice12 price = accountingService.getPackagePrice12(groupId);
      log.info("Successfully retrieved 12-month package price for group: {}", groupId);
      return price;
    } catch (Exception e) {
      log.error("Error fetching 12-month package price for group: {} - Error: {}",
          groupId, e.getMessage(), e);
      throw e;
    }
  }

  @Unsecured
  @QueryMapping
  public PackagePrice24 getPackagePrice24(
      @Argument @Valid @Positive(message = "Group ID must be positive") Integer groupId) {
    log.info("Fetching 24-month package price for group: {}", groupId);
    try {
      PackagePrice24 price = accountingService.getPackagePrice24(groupId);
      log.info("Successfully retrieved 24-month package price for group: {}", groupId);
      return price;
    } catch (Exception e) {
      log.error("Error fetching 24-month package price for group: {} - Error: {}",
          groupId, e.getMessage(), e);
      throw e;
    }
  }

  @Unsecured
  @QueryMapping
  public PackagePrice36 getPackagePrice36(
      @Argument @Valid @Positive(message = "Group ID must be positive") Integer groupId) {
    log.info("Fetching 36-month package price for group: {}", groupId);
    try {
      PackagePrice36 price = accountingService.getPackagePrice36(groupId);
      log.info("Successfully retrieved 36-month package price for group: {}", groupId);
      return price;
    } catch (Exception e) {
      log.error("Error fetching 36-month package price for group: {} - Error: {}",
          groupId, e.getMessage(), e);
      throw e;
    }
  }

  @Unsecured
  @QueryMapping
  public PackagePriceLifetime getPackagePriceLifetime(
      @Argument @Valid @Positive(message = "Group ID must be positive") Integer groupId) {
    log.info("Fetching lifetime package price for group: {}", groupId);
    try {
      PackagePriceLifetime price = accountingService.getPackagePriceLifetime(groupId);
      log.info("Successfully retrieved lifetime package price for group: {}", groupId);
      return price;
    } catch (Exception e) {
      log.error("Error fetching lifetime package price for group: {} - Error: {}",
          groupId, e.getMessage(), e);
      throw e;
    }
  }
}