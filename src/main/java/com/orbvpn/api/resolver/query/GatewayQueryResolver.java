package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.GatewayView;
import com.orbvpn.api.service.GatewayService;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GatewayQueryResolver {
  private final GatewayService gatewayService;

  @Secured(USER)
  @QueryMapping
  public List<GatewayView> gateways() {
    log.info("Fetching all gateways");
    try {
      List<GatewayView> gateways = gatewayService.getAllGateways();
      log.info("Successfully retrieved {} gateways", gateways.size());
      return gateways;
    } catch (Exception e) {
      log.error("Error fetching gateways - Error: {}", e.getMessage(), e);
      throw e;
    }
  }
}