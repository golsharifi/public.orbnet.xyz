package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.AccountingView;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.ServiceGroup;
import com.orbvpn.api.repository.PaymentRepository;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.repository.GroupRepository;
import com.orbvpn.api.service.AccountingService;
import com.orbvpn.api.domain.dto.PackagePrice;
import com.orbvpn.api.domain.dto.PackagePrice3;
import com.orbvpn.api.domain.dto.PackagePrice6;
import com.orbvpn.api.domain.dto.PackagePrice12;
import com.orbvpn.api.domain.dto.PackagePrice24;
import com.orbvpn.api.domain.dto.PackagePrice36;
import com.orbvpn.api.domain.dto.PackagePriceLifetime;
import com.orbvpn.api.domain.dto.PriceView;
import java.math.RoundingMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AccountingService {
  private final UserRepository userRepository;
  private final UserSubscriptionRepository userSubscriptionRepository;
  private final PaymentRepository paymentRepository;
  private final GroupRepository groupRepository;

  public AccountingView getAccounting() {

    AccountingView accountingView = new AccountingView();

    LocalDateTime dateTime = LocalDateTime.now();
    LocalDateTime currentDay = LocalDateTime.of(dateTime.getYear(), dateTime.getMonth(), dateTime.getDayOfMonth(), 0,
        0);
    LocalDateTime currentMonth = LocalDateTime.of(dateTime.getYear(), dateTime.getMonth(), 1, 0, 0);
    LocalDateTime currentYear = LocalDateTime.of(dateTime.getYear(), 1, 1, 0, 0);

    int totalUsers = (int) userRepository.count();
    int joinedByDay = (int) userRepository.countByCreatedAtAfter(currentDay);
    int joinedByMonth = (int) userRepository.countByCreatedAtAfter(currentMonth);
    int joinedByYear = (int) userRepository.countByCreatedAtAfter(currentYear);
    int monthPurchaseCount = userSubscriptionRepository.countTotalSubscriptionCount(currentMonth);
    BigDecimal monthPurchase = userSubscriptionRepository.getTotalSubscriptionPrice(currentMonth);
    int dayPurchaseCount = userSubscriptionRepository.countTotalSubscriptionCount(currentDay);
    BigDecimal dayPurchase = userSubscriptionRepository.getTotalSubscriptionPrice(currentDay);

    int monthRenewPurchaseCount = paymentRepository.getTotalRenewSubscriptionCount(currentMonth);
    BigDecimal monthRenewPurchase = paymentRepository.getTotalRenewSubscriptionPrice(currentMonth);
    int dayRenewPurchaseCount = paymentRepository.getTotalRenewSubscriptionCount(currentDay);
    BigDecimal dayRenewPurchase = paymentRepository.getTotalRenewSubscriptionPrice(currentDay);

    accountingView.setTotalUsers(totalUsers);
    accountingView.setJoinedByDay(joinedByDay);
    accountingView.setJoinedByMonth(joinedByMonth);
    accountingView.setJoinedByYear(joinedByYear);
    accountingView.setMonthPurchaseCount(monthPurchaseCount);
    accountingView.setMonthPurchase(monthPurchase);
    accountingView.setDayPurchaseCount(dayPurchaseCount);
    accountingView.setDayPurchase(dayPurchase);
    accountingView.setMonthRenewPurchaseCount(monthRenewPurchaseCount);
    accountingView.setMonthRenewPurchase(monthRenewPurchase);
    accountingView.setDayRenewPurchaseCount(dayRenewPurchaseCount);
    accountingView.setDayRenewPurchase(dayRenewPurchase);

    return accountingView;
  }

  // public BuyMoreLoginsView getBuyMoreLogins() {
  // User user = userService.getUser();

  // UserSubscription userSubscription =
  // userSubscriptionService.getCurrentSubscription(user);
  // LocalDateTime expiresAt = userSubscription.getExpiresAt();

  // LocalDateTime now = LocalDateTime.now();
  // long daysUntilExpiration = DAYS.between(now, expiresAt);
  // BuyMoreLoginsView buyMoreLoginsView = new BuyMoreLoginsView();
  // double priceForMoreLogins = 0;
  // try {
  // if (daysUntilExpiration > 0) {
  // Group userGroup = userSubscription.getGroup();
  // double discountRateForServiceGroup =
  // userGroup.getServiceGroup().getDiscount().doubleValue() / 100;
  // int durationForAccount = userSubscription.getDuration();
  // BigDecimal groupPrice = userGroup.getPrice();
  // if (durationForAccount > 0) {
  // double groupPricePerDay = groupPrice.doubleValue() * (1 -
  // discountRateForServiceGroup) / durationForAccount;
  // priceForMoreLogins = groupPricePerDay * daysUntilExpiration * (1 -
  // discountRateForServiceGroup);
  // } else {
  // buyMoreLoginsView.setMessage("Your account has expired.");
  // }
  // } else {
  // buyMoreLoginsView.setMessage("Your account has expired.");
  // }
  // } catch (Exception e) {

  // }
  // buyMoreLoginsView.setPriceForMoreLogins(priceForMoreLogins);
  // return buyMoreLoginsView;
  // }

  public PackagePrice getPackagePrice(int groupId) {
    Optional<Group> optionalGroup = groupRepository.findById(groupId);

    if (!optionalGroup.isPresent()) {
      throw new NoSuchElementException("Group not found");
    }

    Group group = optionalGroup.get();
    ServiceGroup serviceGroup = group.getServiceGroup();
    if (serviceGroup == null) {
      throw new NoSuchElementException("Service Pack not found for the provided group");
    }

    BigDecimal discount = serviceGroup.getDiscount();
    if (discount == null) {
      discount = BigDecimal.ZERO; // If discount is null, interpret it as zero
    } else {
      discount = discount.divide(new BigDecimal(100));
    }

    BigDecimal price = group.getPrice();
    if (price == null) {
      throw new IllegalArgumentException("Price is not set for the group");
    }

    BigDecimal discountedPrice = price.multiply(BigDecimal.ONE.subtract(discount));

    return new PackagePrice(groupId, serviceGroup.getId(), discountedPrice.floatValue());
  }

  // Calculate Package Price 3
  public PackagePrice3 getPackagePrice3(int groupId) {
    System.out.println("Getting group with ID: " + groupId);
    Optional<Group> optionalGroup = groupRepository.findById(groupId);

    if (!optionalGroup.isPresent()) {
      System.out.println("Group not found");
      throw new NoSuchElementException("Group not found");
    }

    Group group = optionalGroup.get();
    ServiceGroup serviceGroup = group.getServiceGroup();
    if (serviceGroup == null) {
      System.out.println("Service Pack not found for the provided group");
      throw new NoSuchElementException("Service Pack not found for the provided group");
    }

    BigDecimal discount3 = serviceGroup.getDiscount3();
    if (discount3 == null) {
      System.out.println("Discount is null, interpreting as zero");
      discount3 = BigDecimal.ZERO; // If discount is null, interpret it as zero
    } else {
      System.out.println("Discount found: " + discount3);
      discount3 = discount3.divide(new BigDecimal(100));
    }

    BigDecimal price = group.getPrice();
    if (price == null) {
      System.out.println("Price is not set for the group");
      throw new IllegalArgumentException("Price is not set for the group");
    }

    System.out.println("Calculating discounted price");
    BigDecimal discountedPrice3 = price.multiply(BigDecimal.ONE.subtract(discount3)).multiply(new BigDecimal("3"));

    System.out.println("Returning new PackagePrice3 object");
    return new PackagePrice3(groupId, serviceGroup.getId(), discountedPrice3.floatValue());
  }

  // Calculate Package Price 6
  public PackagePrice6 getPackagePrice6(int groupId) {
    System.out.println("Getting group with ID: " + groupId);
    Optional<Group> optionalGroup = groupRepository.findById(groupId);

    if (!optionalGroup.isPresent()) {
      System.out.println("Group not found");
      throw new NoSuchElementException("Group not found");
    }

    Group group = optionalGroup.get();
    ServiceGroup serviceGroup = group.getServiceGroup();
    if (serviceGroup == null) {
      System.out.println("Service Pack not found for the provided group");
      throw new NoSuchElementException("Service Pack not found for the provided group");
    }

    BigDecimal discount6 = serviceGroup.getDiscount6();
    if (discount6 == null) {
      System.out.println("Discount is null, interpreting as zero");
      discount6 = BigDecimal.ZERO; // If discount is null, interpret it as zero
    } else {
      System.out.println("Discount found: " + discount6);
      discount6 = discount6.divide(new BigDecimal(100));
    }

    BigDecimal price = group.getPrice();
    if (price == null) {
      System.out.println("Price is not set for the group");
      throw new IllegalArgumentException("Price is not set for the group");
    }

    System.out.println("Calculating discounted price");
    BigDecimal discountedPrice6 = price.multiply(BigDecimal.ONE.subtract(discount6)).multiply(new BigDecimal("6"));

    System.out.println("Returning new PackagePrice6 object");
    return new PackagePrice6(groupId, serviceGroup.getId(), discountedPrice6.floatValue());
  }

  // Calculate Package Price 12
  public PackagePrice12 getPackagePrice12(int groupId) {
    System.out.println("Getting group with ID: " + groupId);
    Optional<Group> optionalGroup = groupRepository.findById(groupId);

    if (!optionalGroup.isPresent()) {
      System.out.println("Group not found");
      throw new NoSuchElementException("Group not found");
    }

    Group group = optionalGroup.get();
    ServiceGroup serviceGroup = group.getServiceGroup();
    if (serviceGroup == null) {
      System.out.println("Service Pack not found for the provided group");
      throw new NoSuchElementException("Service Pack not found for the provided group");
    }

    BigDecimal discount12 = serviceGroup.getDiscount12();
    if (discount12 == null) {
      System.out.println("Discount is null, interpreting as zero");
      discount12 = BigDecimal.ZERO; // If discount is null, interpret it as zero
    } else {
      System.out.println("Discount found: " + discount12);
      discount12 = discount12.divide(new BigDecimal(100));
    }

    BigDecimal price = group.getPrice();
    if (price == null) {
      System.out.println("Price is not set for the group");
      throw new IllegalArgumentException("Price is not set for the group");
    }

    System.out.println("Calculating discounted price");
    BigDecimal discountedPrice12 = price.multiply(BigDecimal.ONE.subtract(discount12)).multiply(new BigDecimal("12"));

    System.out.println("Returning new PackagePrice12 object");
    return new PackagePrice12(groupId, serviceGroup.getId(), discountedPrice12.floatValue());
  }

  // Calculate Package Price 24
  public PackagePrice24 getPackagePrice24(int groupId) {
    System.out.println("Getting group with ID: " + groupId);
    Optional<Group> optionalGroup = groupRepository.findById(groupId);

    if (!optionalGroup.isPresent()) {
      System.out.println("Group not found");
      throw new NoSuchElementException("Group not found");
    }

    Group group = optionalGroup.get();
    ServiceGroup serviceGroup = group.getServiceGroup();
    if (serviceGroup == null) {
      System.out.println("Service Pack not found for the provided group");
      throw new NoSuchElementException("Service Pack not found for the provided group");
    }

    BigDecimal discount24 = serviceGroup.getDiscount24();
    if (discount24 == null) {
      System.out.println("Discount is null, interpreting as zero");
      discount24 = BigDecimal.ZERO; // If discount is null, interpret it as zero
    } else {
      System.out.println("Discount found: " + discount24);
      discount24 = discount24.divide(new BigDecimal(100));
    }

    BigDecimal price = group.getPrice();
    if (price == null) {
      System.out.println("Price is not set for the group");
      throw new IllegalArgumentException("Price is not set for the group");
    }

    System.out.println("Calculating discounted price");
    BigDecimal discountedPrice24 = price.multiply(BigDecimal.ONE.subtract(discount24)).multiply(new BigDecimal("24"));

    System.out.println("Returning new PackagePrice24 object");
    return new PackagePrice24(groupId, serviceGroup.getId(), discountedPrice24.floatValue());
  }

  // Calculate Package Price 36
  public PackagePrice36 getPackagePrice36(int groupId) {
    System.out.println("Getting group with ID: " + groupId);
    Optional<Group> optionalGroup = groupRepository.findById(groupId);

    if (!optionalGroup.isPresent()) {
      System.out.println("Group not found");
      throw new NoSuchElementException("Group not found");
    }

    Group group = optionalGroup.get();
    ServiceGroup serviceGroup = group.getServiceGroup();
    if (serviceGroup == null) {
      System.out.println("Service Pack not found for the provided group");
      throw new NoSuchElementException("Service Pack not found for the provided group");
    }

    BigDecimal discount36 = serviceGroup.getDiscount36();
    if (discount36 == null) {
      System.out.println("Discount is null, interpreting as zero");
      discount36 = BigDecimal.ZERO; // If discount is null, interpret it as zero
    } else {
      System.out.println("Discount found: " + discount36);
      discount36 = discount36.divide(new BigDecimal(100));
    }

    BigDecimal price = group.getPrice();
    if (price == null) {
      System.out.println("Price is not set for the group");
      throw new IllegalArgumentException("Price is not set for the group");
    }

    System.out.println("Calculating discounted price");
    BigDecimal discountedPrice36 = price.multiply(BigDecimal.ONE.subtract(discount36)).multiply(new BigDecimal("36"));

    System.out.println("Returning new PackagePrice36 object");
    return new PackagePrice36(groupId, serviceGroup.getId(), discountedPrice36.floatValue());
  }

  // Calculate Package Price Lifetime
  public PackagePriceLifetime getPackagePriceLifetime(int groupId) {
    System.out.println("Getting group with ID: " + groupId);
    Optional<Group> optionalGroup = groupRepository.findById(groupId);

    if (!optionalGroup.isPresent()) {
      System.out.println("Group not found");
      throw new NoSuchElementException("Group not found");
    }

    Group group = optionalGroup.get();
    ServiceGroup serviceGroup = group.getServiceGroup();
    if (serviceGroup == null) {
      System.out.println("Service Pack not found for the provided group");
      throw new NoSuchElementException("Service Pack not found for the provided group");
    }

    BigDecimal discountLifetime = serviceGroup.getDiscountLifetime();
    if (discountLifetime == null) {
      System.out.println("Discount is null, interpreting as zero");
      discountLifetime = BigDecimal.ZERO; // If discount is null, interpret it as zero
    } else {
      System.out.println("Discount found: " + discountLifetime);
      discountLifetime = discountLifetime.divide(new BigDecimal(100));
    }

    BigDecimal price = group.getPrice();
    if (price == null) {
      System.out.println("Price is not set for the group");
      throw new IllegalArgumentException("Price is not set for the group");
    }

    System.out.println("Calculating discounted price");
    BigDecimal discountedPriceLifetime = price.multiply(BigDecimal.ONE.subtract(discountLifetime))
        .multiply(new BigDecimal("120"));

    System.out.println("Returning new PackagePriceLifetime object");
    return new PackagePriceLifetime(groupId, serviceGroup.getId(), discountedPriceLifetime.floatValue());
  }

  public PriceView calculateDiscountedPriceByDuration(Group group) {
    int duration = group.getDuration();
    BigDecimal price = group.getPrice();
    BigDecimal discountRate = BigDecimal.ZERO;

    // Define discount rate based on duration
    if (duration < 30) {
      discountRate = BigDecimal.ZERO;
    } else if (duration < 90) {
      discountRate = group.getServiceGroup().getDiscount();
    } else if (duration < 180) {
      discountRate = group.getServiceGroup().getDiscount3();
    } else if (duration < 365) {
      discountRate = group.getServiceGroup().getDiscount6();
    } else if (duration < 730) {
      discountRate = group.getServiceGroup().getDiscount12();
    } else if (duration < 1095) {
      discountRate = group.getServiceGroup().getDiscount24();
    } else if (duration < 3650) {
      discountRate = group.getServiceGroup().getDiscount36();
    } else {
      discountRate = group.getServiceGroup().getDiscountLifetime(); // Assuming lifetime discount for >= 1095 days
    }

    if (discountRate == null) {
      discountRate = BigDecimal.ZERO;
    }

    BigDecimal durationBigDecimal = new BigDecimal(duration);
    System.out.println("Duration in BigDecimal: " + durationBigDecimal);

    BigDecimal durationMonthsBigDecimal = durationBigDecimal.divide(new BigDecimal("30"), 0, RoundingMode.FLOOR);

    int durationMonths = durationMonthsBigDecimal.intValue();
    System.out.println("Duration in months: " + durationMonths);

    BigDecimal discountRateDecimal = discountRate.divide(new BigDecimal("100"));
    System.out.println("Discount rate in decimal: " + discountRateDecimal);

    BigDecimal discountedPercentage = BigDecimal.ONE.subtract(discountRateDecimal);
    System.out.println("Discounted percentage: " + discountedPercentage);

    BigDecimal discountedPrice = price.multiply(discountedPercentage);
    System.out.println("Discounted price: " + discountedPrice);

    BigDecimal savingRate = BigDecimal.ONE
        .subtract(discountedPrice.divide(price, 2, RoundingMode.HALF_UP));
    System.out.println("Saving rate: " + savingRate);

    return new PriceView(discountedPrice.doubleValue(), savingRate, discountRate, durationMonths);

  }

}