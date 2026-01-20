package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.UserView;
import com.orbvpn.api.service.reseller.ResellerUserService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.RESELLER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ResellerUserQueryResolver {
  private final ResellerUserService resellerUserService;

  @Secured({ ADMIN, RESELLER })
  @QueryMapping
  public UserView resellerGetUserByEmail(@Argument String email) {
    log.info("Fetching user by email: {}", email);
    try {
      UserView user = resellerUserService.getUserByEmail(email);
      log.info("Successfully retrieved user for email: {}", email);
      return user;
    } catch (Exception e) {
      log.error("Error fetching user by email: {} - Error: {}", email, e.getMessage(), e);
      throw e;
    }
  }

  @Secured({ ADMIN, RESELLER })
  @QueryMapping
  public UserView resellerGetUserByUsername(@Argument String username) {
    log.info("Fetching user by username: {}", username);
    try {
      UserView user = resellerUserService.getUserByUsername(username);
      log.info("Successfully retrieved user for username: {}", username);
      return user;
    } catch (Exception e) {
      log.error("Error fetching user by username: {} - Error: {}", username, e.getMessage(), e);
      throw e;
    }
  }

  @Secured({ ADMIN, RESELLER })
  @QueryMapping
  public UserView resellerGetUserById(@Argument Integer id) {
    log.info("Fetching user by id: {}", id);
    try {
      UserView user = resellerUserService.getUserById(id);
      log.info("Successfully retrieved user for id: {}", id);
      return user;
    } catch (Exception e) {
      log.error("Error fetching user by id: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }

  @Secured({ ADMIN, RESELLER })
  @QueryMapping
  public Page<UserView> resellerGetUsers(@Argument int page, @Argument int size) {
    log.info("Fetching users page {} with size {}", page, size);
    try {
      return resellerUserService.getUsers(page, size);
    } catch (Exception e) {
      log.error("Error fetching users page: {} - Error: {}", page, e.getMessage(), e);
      throw e;
    }
  }

  @Secured({ ADMIN, RESELLER })
  @QueryMapping
  public Page<UserView> resellerGetExpiredUsers(@Argument int page, @Argument int size) {
    log.info("Fetching expired users page {} with size {}", page, size);
    try {
      return resellerUserService.getExpiredUsers(page, size);
    } catch (Exception e) {
      log.error("Error fetching expired users page: {} - Error: {}", page, e.getMessage(), e);
      throw e;
    }
  }
}