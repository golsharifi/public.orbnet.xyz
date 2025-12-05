package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.ServerEdit;
import com.orbvpn.api.domain.dto.ServerView;
import com.orbvpn.api.service.ServerService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ServerMutationResolver {

  private final ServerService serverService;

  @Secured(ADMIN)
  @MutationMapping
  public ServerView createServer(@Argument @Valid ServerEdit server) {
    log.info("Creating new server");
    try {
      return serverService.createServer(server);
    } catch (Exception e) {
      log.error("Error creating server - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @MutationMapping
  public ServerView editServer(
      @Argument @Valid @Min(1) int id,
      @Argument @Valid ServerEdit serverEdit) {
    log.info("Editing server: {}", id);
    try {
      return serverService.editServer(id, serverEdit);
    } catch (Exception e) {
      log.error("Error editing server: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @MutationMapping
  public ServerView deleteServer(@Argument @Valid @Min(1) int id) {
    log.info("Deleting server: {}", id);
    try {
      return serverService.deleteServer(id);
    } catch (Exception e) {
      log.error("Error deleting server: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }
}