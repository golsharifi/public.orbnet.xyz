package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.UserProfileView;
import com.orbvpn.api.domain.dto.UserSubscriptionView;
import com.orbvpn.api.domain.dto.UserView;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.mapper.UserSubscriptionViewMapper;
import com.orbvpn.api.mapper.UserViewMapper;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.exception.NotFoundException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.constraints.Email;
import java.util.List;
import java.util.stream.Collectors;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class UserQueryResolver {
  private final UserService userService;
  private final UserSubscriptionViewMapper userSubscriptionViewMapper;
  private final UserViewMapper userViewMapper;

  @Secured(USER)
  @QueryMapping
  public UserView user() {
    log.info("Fetching current user information");
    try {
      UserView user = userService.getUserView();
      log.info("Successfully retrieved user information");
      return user;
    } catch (Exception e) {
      log.error("Error fetching user information - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(USER)
  @QueryMapping
  public UserProfileView userProfile() {
    log.info("Fetching user profile");
    try {
      UserProfileView profile = userService.getProfile();
      log.info("Successfully retrieved user profile");
      return profile;
    } catch (Exception e) {
      log.error("Error fetching user profile - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(USER)
  @QueryMapping
  public UserSubscriptionView userSubscription() {
    log.info("Fetching current user subscription");
    try {
      UserSubscriptionView subscription = userService.getUserSubscription();
      log.info("Successfully retrieved user subscription");
      return subscription;
    } catch (Exception e) {
      log.error("Error fetching user subscription - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(USER)
  @QueryMapping
  public UserSubscriptionView userSubscriptionByEmail(@Argument @Email String email) {
    log.info("Fetching user subscription for email: {}", email);
    try {
      User user = userService.getUserByEmail(email);
      UserSubscription currentSubscription = user.getCurrentSubscription();

      if (currentSubscription == null) {
        log.warn("No active subscription found for user with email: {}", email);
        throw new NotFoundException("No active subscription found for user.");
      }

      UserSubscriptionView subscriptionView = userSubscriptionViewMapper.toView(currentSubscription);
      log.info("Successfully retrieved subscription for user: {}", email);
      return subscriptionView;
    } catch (Exception e) {
      log.error("Error fetching subscription for user: {} - Error: {}", email, e.getMessage(), e);
      throw e;
    }
  }

  @Secured(USER)
  @QueryMapping
  public List<UserSubscriptionView> userSubscriptionList(@Argument @Email String email) {
    log.info("Fetching subscription list for user: {}", email);
    try {
      User user = userService.getUserByEmail(email);
      List<UserSubscriptionView> subscriptions = user.getUserSubscriptionList().stream()
          .map(userSubscriptionViewMapper::toView)
          .collect(Collectors.toList());

      log.info("Successfully retrieved {} subscriptions for user: {}",
          subscriptions.size(), email);
      return subscriptions;
    } catch (Exception e) {
      log.error("Error fetching subscription list for user: {} - Error: {}",
          email, e.getMessage(), e);
      throw e;
    }
  }

  @Secured(USER)
  @QueryMapping
  public UserView getUserInfo() {
    log.debug("Fetching current user info with UUID");
    User user = userService.getCurrentUserWithUuid();
    return userViewMapper.toView(user);
  }
}