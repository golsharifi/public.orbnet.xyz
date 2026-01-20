package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.ClientServerView;
import com.orbvpn.api.domain.dto.ServerView;
import com.orbvpn.api.service.ServerService;
import com.orbvpn.api.exception.NotFoundException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ServerQueryResolver {

  private final ServerService serverService;

  @Secured(ADMIN)
  @QueryMapping
  public List<ServerView> servers() {
    log.info("Fetching all servers");
    try {
      List<ServerView> servers = serverService.getServers();
      log.info("Successfully retrieved {} servers", servers.size());
      return servers;
    } catch (Exception e) {
      log.error("Error fetching servers - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(USER)
  @QueryMapping
  public List<ClientServerView> clientServers() {
    log.info("Fetching client servers");
    try {
      List<ClientServerView> servers = serverService.getClientServers();
      log.info("Successfully retrieved {} client servers", servers.size());
      return servers;
    } catch (Exception e) {
      log.error("Error fetching client servers - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(USER)
  @QueryMapping
  public List<ClientServerView> getClientSortedServers(
      @Argument String sortBy,
      @Argument String parameter) {

    log.info("Fetching sorted client servers - sortBy: {}, parameter: {}", sortBy, parameter);
    try {
      List<ClientServerView> servers = serverService.getClientSortedServers(sortBy, parameter);
      log.info("Successfully retrieved {} sorted client servers", servers.size());
      return servers;
    } catch (Exception e) {
      log.error("Error fetching sorted client servers - sortBy: {}, parameter: {} - Error: {}",
          sortBy, parameter, e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @QueryMapping
  public ServerView server(@Argument @Valid @Positive(message = "Server ID must be positive") Integer id) {
    log.info("Fetching server with id: {}", id);
    try {
      ServerView server = serverService.getServer(id);
      if (server == null) {
        throw new NotFoundException("Server not found with id: " + id);
      }
      log.info("Successfully retrieved server: {}", id);
      return server;
    } catch (NotFoundException e) {
      log.warn("Server not found - ID: {} - Error: {}", id, e.getMessage());
      throw e;
    } catch (Exception e) {
      log.error("Error fetching server: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }
}