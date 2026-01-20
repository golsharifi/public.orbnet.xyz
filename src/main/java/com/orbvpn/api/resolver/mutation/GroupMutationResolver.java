package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.GroupEdit;
import com.orbvpn.api.domain.dto.GroupView;
import com.orbvpn.api.service.GroupService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GroupMutationResolver {

  private final GroupService groupService;

  @Secured(ADMIN)
  @MutationMapping
  public GroupView createGroup(@Argument @Valid GroupEdit group) {
    log.info("Creating new group");
    try {
      return groupService.createGroup(group);
    } catch (Exception e) {
      log.error("Error creating group - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @MutationMapping
  public GroupView editGroup(
      @Argument @Valid @Positive(message = "Group ID must be positive") int id,
      @Argument @Valid GroupEdit group) {
    log.info("Editing group: {}", id);
    try {
      return groupService.editGroup(id, group);
    } catch (Exception e) {
      log.error("Error editing group: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @MutationMapping
  public GroupView deleteGroup(@Argument @Valid @Positive(message = "Group ID must be positive") int id) {
    log.info("Deleting group: {}", id);
    try {
      return groupService.deleteGroup(id);
    } catch (Exception e) {
      log.error("Error deleting group: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }
}