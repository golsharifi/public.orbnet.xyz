package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.staticip.*;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.*;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.staticip.PortForwardService;
import com.orbvpn.api.service.staticip.StaticIPService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.stream.Collectors;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class StaticIPQueryResolver {

    private final StaticIPService staticIPService;
    private final PortForwardService portForwardService;
    private final UserService userService;

    @Secured(USER)
    @QueryMapping
    public StaticIPDashboardDTO staticIPDashboard() {
        log.info("Fetching static IP dashboard for current user");
        try {
            User user = userService.getUser();

            return StaticIPDashboardDTO.builder()
                    .subscription(staticIPService.getUserSubscription(user).orElse(null))
                    .allocations(staticIPService.getUserAllocations(user))
                    .availablePlans(staticIPService.getPlans())
                    .availableRegions(staticIPService.getAvailableRegions())
                    .addonPlans(getAddonPlans())
                    .build();
        } catch (Exception e) {
            log.error("Error fetching static IP dashboard: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public List<StaticIPPlanDTO> staticIPPlans() {
        log.info("Fetching static IP plans");
        return staticIPService.getPlans();
    }

    @Secured(USER)
    @QueryMapping
    public List<RegionAvailabilityDTO> staticIPRegions() {
        log.info("Fetching available static IP regions");
        return staticIPService.getAvailableRegions();
    }

    @Secured(USER)
    @QueryMapping
    public StaticIPSubscription myStaticIPSubscription() {
        log.info("Fetching static IP subscription for current user");
        User user = userService.getUser();
        return staticIPService.getUserSubscription(user).orElse(null);
    }

    @Secured(USER)
    @QueryMapping
    public List<StaticIPAllocation> myStaticIPAllocations() {
        log.info("Fetching static IP allocations for current user");
        User user = userService.getUser();
        return staticIPService.getUserAllocations(user);
    }

    @Secured(USER)
    @QueryMapping
    public StaticIPAllocation staticIPAllocation(@Argument Long allocationId) {
        log.info("Fetching static IP allocation: {}", allocationId);
        // TODO: Implement with ownership check
        return null;
    }

    @Secured(USER)
    @QueryMapping
    public List<PortForwardRuleDTO> portForwardRules(@Argument Long allocationId) {
        log.info("Fetching port forward rules for allocation: {}", allocationId);
        User user = userService.getUser();
        return portForwardService.getPortForwardRules(user, allocationId);
    }

    @Secured(USER)
    @QueryMapping
    public PortForwardService.PortForwardLimits portForwardLimits(@Argument Long allocationId) {
        log.info("Fetching port forward limits for allocation: {}", allocationId);
        User user = userService.getUser();
        return portForwardService.getPortForwardLimits(user, allocationId);
    }

    @Secured(USER)
    @QueryMapping
    public List<PortForwardAddon> myPortForwardAddons(@Argument Long allocationId) {
        log.info("Fetching port forward addons for allocation: {}", allocationId);
        // TODO: Implement
        return List.of();
    }

    @Secured(USER)
    @QueryMapping
    public List<PortForwardAddonPlanDTO> portForwardAddonPlans() {
        log.info("Fetching available port forward addon plans");
        return getAddonPlans();
    }

    // Admin endpoints

    @Secured(ADMIN)
    @QueryMapping
    public StaticIPAdminStatsDTO adminStaticIPStats() {
        log.info("Fetching static IP admin statistics");
        // TODO: Implement admin stats
        return StaticIPAdminStatsDTO.builder()
                .totalSubscriptions(0)
                .activeSubscriptions(0)
                .totalAllocations(0)
                .activeAllocations(0)
                .build();
    }

    @Secured(ADMIN)
    @QueryMapping
    public StaticIPSubscription adminGetUserStaticIPSubscription(@Argument Integer userId) {
        log.info("Admin fetching static IP subscription for user: {}", userId);
        User user = userService.getUserById(userId);
        return staticIPService.getUserSubscription(user).orElse(null);
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<StaticIPAllocation> adminGetUserStaticIPAllocations(@Argument Integer userId) {
        log.info("Admin fetching static IP allocations for user: {}", userId);
        User user = userService.getUserById(userId);
        return staticIPService.getUserAllocations(user);
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<StaticIPPoolStatsDTO> adminStaticIPPoolStats() {
        log.info("Fetching static IP pool statistics");
        // TODO: Implement pool stats
        return List.of();
    }

    // Helper methods

    private List<PortForwardAddonPlanDTO> getAddonPlans() {
        return java.util.Arrays.stream(PortForwardAddonType.values())
                .map(type -> PortForwardAddonPlanDTO.builder()
                        .addonType(type)
                        .name(type.getDisplayName())
                        .ports(type.getPorts())
                        .priceMonthly(type.getPriceMonthly())
                        .build())
                .collect(Collectors.toList());
    }
}
