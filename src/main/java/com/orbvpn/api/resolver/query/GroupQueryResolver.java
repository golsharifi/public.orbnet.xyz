package com.orbvpn.api.resolver.query;

import com.orbvpn.api.config.security.Unsecured;
import com.orbvpn.api.domain.dto.GroupView;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.exception.NotFoundException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GroupQueryResolver {
  private final GroupService groupService;

  @QueryMapping
  public List<GroupView> groups(
      @Argument @Valid @Positive(message = "Service Group ID must be positive") Integer serviceGroupId) {
    log.info("Fetching groups for service group: {}", serviceGroupId);
    try {
      List<GroupView> groups = groupService.getGroups(serviceGroupId);
      log.info("Successfully retrieved {} groups for service group: {}", groups.size(), serviceGroupId);
      return groups;
    } catch (Exception e) {
      log.error("Error fetching groups for service group: {} - Error: {}",
          serviceGroupId, e.getMessage(), e);
      throw e;
    }
  }

  @Unsecured
  @QueryMapping
  public List<GroupView> registrationGroups() {
    log.info("Fetching registration groups");
    try {
      List<GroupView> groups = groupService.getRegistrationGroups();
      log.info("Successfully retrieved {} registration groups", groups.size());
      return groups;
    } catch (Exception e) {
      log.error("Error fetching registration groups - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @QueryMapping
  public List<GroupView> allGroups() {
    log.info("Fetching all groups");
    try {
      List<GroupView> groups = groupService.getAllGroups();
      log.info("Successfully retrieved {} groups", groups.size());
      return groups;
    } catch (Exception e) {
      log.error("Error fetching all groups - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @QueryMapping
  public GroupView group(@Argument @Valid @Positive(message = "Group ID must be positive") Integer id) {
    log.info("Fetching group with id: {}", id);
    try {
      GroupView group = groupService.getGroup(id);
      if (group == null) {
        throw new NotFoundException("Group not found with id: " + id);
      }
      log.info("Successfully retrieved group: {}", id);
      return group;
    } catch (NotFoundException e) {
      log.warn("Group not found - ID: {} - Error: {}", id, e.getMessage());
      throw e;
    } catch (Exception e) {
      log.error("Error fetching group: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }
}