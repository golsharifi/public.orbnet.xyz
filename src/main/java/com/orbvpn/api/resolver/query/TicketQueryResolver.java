package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.TicketView;
import com.orbvpn.api.domain.enums.TicketCategory;
import com.orbvpn.api.domain.enums.TicketStatus;
import com.orbvpn.api.service.HelpCenterService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import java.util.List;
import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TicketQueryResolver {
  private final HelpCenterService helpCenterService;

  @Secured({ ADMIN, USER })
  @QueryMapping
  public Page<TicketView> tickets(
      @Argument Integer page,
      @Argument Integer size,
      @Argument TicketCategory category,
      @Argument TicketStatus status) {
    log.info("Fetching tickets - page: {}, size: {}, category: {}, status: {}",
        page, size, category, status);
    try {
      Page<TicketView> ticketsPage = helpCenterService.getTickets(page, size, category, status);
      log.info("Successfully retrieved {} tickets", ticketsPage.getTotalElements());
      return ticketsPage;
    } catch (Exception e) {
      log.error("Error fetching tickets - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(USER)
  @QueryMapping
  public List<TicketView> userTickets() {
    log.info("Fetching tickets for current user");
    try {
      List<TicketView> tickets = helpCenterService.getUserTickets();
      log.info("Successfully retrieved {} user tickets", tickets.size());
      return tickets;
    } catch (Exception e) {
      log.error("Error fetching user tickets - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured({ ADMIN, USER })
  @QueryMapping
  public TicketView ticket(@Argument Integer id) {
    log.info("Fetching ticket with id: {}", id);
    try {
      TicketView ticket = helpCenterService.getTicketView(id);
      log.info("Successfully retrieved ticket: {}", id);
      return ticket;
    } catch (Exception e) {
      log.error("Error fetching ticket: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @QueryMapping
  public List<TicketView> getUserTickets(@Argument Integer userId) {
    log.info("Fetching tickets for user: {}", userId);
    try {
      List<TicketView> tickets = helpCenterService.getTicketsByUserId(userId);
      log.info("Successfully retrieved {} tickets for user: {}", tickets.size(), userId);
      return tickets;
    } catch (Exception e) {
      log.error("Error fetching tickets for user: {} - Error: {}", userId, e.getMessage(), e);
      throw e;
    }
  }
}