package com.orbvpn.api.resolver.mutation;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

import com.orbvpn.api.domain.dto.TicketCreate;
import com.orbvpn.api.domain.dto.TicketReplyCreate;
import com.orbvpn.api.domain.dto.TicketReplyView;
import com.orbvpn.api.domain.dto.TicketView;
import com.orbvpn.api.service.HelpCenterService;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TicketMutationResolver {

  private final HelpCenterService helpCenterService;

  @MutationMapping
  public TicketView createTicket(@Argument @Valid TicketCreate ticket) {
    log.info("Creating ticket");
    try {
      return helpCenterService.createTicket(ticket);
    } catch (Exception e) {
      log.error("Error creating ticket - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @MutationMapping
  public TicketView createTicketForUser(
      @Argument @Valid @Min(1) Integer userId,
      @Argument @Valid TicketCreate ticket) {
    log.info("Creating ticket for user: {}", userId);
    try {
      return helpCenterService.createTicketForUser(userId, ticket);
    } catch (Exception e) {
      log.error("Error creating ticket for user: {} - Error: {}", userId, e.getMessage(), e);
      throw e;
    }
  }

  @MutationMapping
  public TicketView closeTicket(@Argument @Valid @Min(1) int id) {
    log.info("Closing ticket: {}", id);
    try {
      return helpCenterService.closeTicket(id);
    } catch (Exception e) {
      log.error("Error closing ticket: {} - Error: {}", id, e.getMessage(), e);
      throw e;
    }
  }

  @Secured(ADMIN)
  @MutationMapping
  public List<TicketView> closeTickets(@Argument List<@Valid @Min(1) Integer> ids) {
    log.info("Closing tickets: {}", ids);
    try {
      return helpCenterService.closeTickets(ids);
    } catch (Exception e) {
      log.error("Error closing tickets - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @MutationMapping
  public TicketReplyView replyToTicket(
      @Argument @Valid @Min(1) int ticketId,
      @Argument @Valid TicketReplyCreate reply) {
    log.info("Adding reply to ticket: {}", ticketId);
    try {
      return helpCenterService.replyToTicket(ticketId, reply);
    } catch (Exception e) {
      log.error("Error replying to ticket: {} - Error: {}", ticketId, e.getMessage(), e);
      throw e;
    }
  }
}