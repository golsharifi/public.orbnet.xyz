package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.ServiceGroupEdit;
import com.orbvpn.api.domain.dto.ServiceGroupView;
import com.orbvpn.api.service.ServiceGroupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Controller
@Slf4j
@RequiredArgsConstructor
public class ServiceGroupMutationResolver {

  private final ServiceGroupService serviceGroupService;

  @Secured(ADMIN)
  @MutationMapping
  public ServiceGroupView createServiceGroup(@Argument @Valid ServiceGroupEdit serviceGroup) {
    log.info("Creating service group");
    try {
      return serviceGroupService.createServiceGroup(serviceGroup);
    } catch (Exception e) {
      log.error("Error creating service group - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @MutationMapping
  public ServiceGroupView editServiceGroup(
      @Argument @Valid @Min(1) int id,
      @Argument @Valid ServiceGroupEdit serviceGroupEdit) {
    log.info("Editing service group: {}", id);
    try {
      return serviceGroupService.editServiceGroup(id, serviceGroupEdit);
    } catch (Exception e) {
      log.error("Error editing service group: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @MutationMapping
  public ServiceGroupView deleteServiceGroup(@Argument @Valid @Min(1) int id) {
    log.info("Deleting service group: {}", id);
    try {
      return serviceGroupService.deleteServiceGroup(id);
    } catch (Exception e) {
      log.error("Error deleting service group: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }
}
