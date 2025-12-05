package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.ServiceGroupView;
import com.orbvpn.api.service.ServiceGroupService;
import com.orbvpn.api.exception.NotFoundException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;
import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ServiceGroupQueryResolver {

  private final ServiceGroupService serviceGroupService;

  @Secured({USER, ADMIN})
  @QueryMapping
  @Transactional(readOnly = true)
  public List<ServiceGroupView> serviceGroups() {
    log.info("Fetching all service groups");
    try {
      List<ServiceGroupView> groups = serviceGroupService.getAllServiceGroups();
      log.info("Successfully retrieved {} service groups", groups.size());
      return groups;
    } catch (Exception e) {
      log.error("Error fetching service groups - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured({USER, ADMIN})
  @QueryMapping
  @Transactional(readOnly = true)
  public ServiceGroupView serviceGroup(@Argument @Valid @Positive(message = "ID must be positive") Integer id) {
    log.info("Fetching service group with id: {}", id);
    try {
      ServiceGroupView group = serviceGroupService.getServiceGroup(id);
      if (group == null) {
        throw new NotFoundException("Service group not found with id: " + id);
      }
      log.info("Successfully retrieved service group: {}", id);
      return group;
    } catch (NotFoundException e) {
      log.warn("Service group not found - ID: {} - Error: {}", id, e.getMessage());
      throw e;
    } catch (Exception e) {
      log.error("Error fetching service group: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }
}