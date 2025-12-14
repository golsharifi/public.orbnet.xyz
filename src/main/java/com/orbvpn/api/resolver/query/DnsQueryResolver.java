package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.DnsServiceType;
import com.orbvpn.api.service.DnsService;
import com.orbvpn.api.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.*;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DnsQueryResolver {

    private final DnsService dnsService;
    private final UserService userService;

    // ========================================================================
    // STREAMING SERVICES QUERIES
    // ========================================================================

    @Secured(USER)
    @QueryMapping
    public List<DnsServiceCategoryView> dnsServices() {
        log.info("Fetching all DNS streaming services");
        return dnsService.getStreamingServices();
    }

    @Secured(USER)
    @QueryMapping
    public List<DnsServiceView> dnsServicesByRegion(@Argument String region) {
        log.info("Fetching DNS services for region: {}", region);
        return dnsService.getStreamingServicesByRegion(region);
    }

    @Secured(USER)
    @QueryMapping
    public List<DnsServiceView> dnsPopularServices() {
        log.info("Fetching popular DNS services");
        return dnsService.getPopularStreamingServices();
    }

    @Secured(USER)
    @QueryMapping
    public List<DnsServiceView> dnsSearchServices(@Argument String query) {
        log.info("Searching DNS services for: {}", query);
        return dnsService.searchStreamingServices(query);
    }

    // ========================================================================
    // REGIONAL SERVICES QUERIES
    // ========================================================================

    @Secured(USER)
    @QueryMapping
    public List<DnsRegionalCategoryView> dnsRegionalServices() {
        log.info("Fetching all Regional DNS services");
        return dnsService.getRegionalServices();
    }

    @Secured(USER)
    @QueryMapping
    public List<DnsRegionalServiceView> dnsRegionalServicesByCategory(@Argument String categoryId) {
        log.info("Fetching Regional services for category: {}", categoryId);
        return dnsService.getRegionalServicesByCategory(categoryId);
    }

    // ========================================================================
    // USER RULES QUERIES
    // ========================================================================

    @Secured(USER)
    @QueryMapping
    public List<DnsUserRuleView> myDnsRules() {
        User user = userService.getUser();
        log.info("Fetching DNS rules for user: {}", user.getId());
        return dnsService.getUserRules(user);
    }

    @Secured(USER)
    @QueryMapping
    public DnsUserRuleView myDnsRule(@Argument String serviceId, @Argument DnsServiceType serviceType) {
        User user = userService.getUser();
        log.info("Fetching DNS rule for user: {}, service: {}", user.getId(), serviceId);
        return dnsService.getUserRule(user, serviceId, serviceType);
    }

    // ========================================================================
    // WHITELIST QUERIES
    // ========================================================================

    @Secured(USER)
    @QueryMapping
    public List<DnsWhitelistedIpView> myWhitelistedIps() {
        User user = userService.getUser();
        log.info("Fetching whitelisted IPs for user: {}", user.getId());
        return dnsService.getWhitelistedIps(user);
    }

    // ========================================================================
    // CONFIG AND STATS QUERIES
    // ========================================================================

    @Secured(USER)
    @QueryMapping
    public DnsConfigView dnsConfig() {
        User user = userService.getUser();
        log.info("Fetching DNS config for user: {}", user.getId());
        return dnsService.getConfigForUser(user);
    }

    @Secured(USER)
    @QueryMapping
    public List<DnsRegionalServerView> dnsRegionalServers() {
        log.info("Fetching DNS regional servers");
        return dnsService.getRegionalServers();
    }

    @Secured(USER)
    @QueryMapping
    public DnsUserStatsView myDnsStats() {
        User user = userService.getUser();
        log.info("Fetching DNS stats for user: {}", user.getId());
        return dnsService.getUserStats(user);
    }

    // ========================================================================
    // ADMIN QUERIES
    // ========================================================================

    @Secured(ADMIN)
    @QueryMapping
    public DnsAdminStatsView dnsAdminStats() {
        log.info("Fetching DNS admin stats");
        return dnsService.getAdminStats();
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<DnsUserOverviewView> dnsAdminUserOverview(
            @Argument Integer limit,
            @Argument Integer offset) {
        log.info("Fetching DNS admin user overview");
        return dnsService.getAdminUserOverview(
            limit != null ? limit : 50,
            offset != null ? offset : 0
        );
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<DnsUserRuleView> dnsAdminUserRules(@Argument int userId) {
        log.info("Fetching DNS rules for user: {} (admin)", userId);
        return dnsService.getAdminUserRules(userId);
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<DnsWhitelistedIpView> dnsAdminUserWhitelist(@Argument int userId) {
        log.info("Fetching whitelisted IPs for user: {} (admin)", userId);
        return dnsService.getAdminUserWhitelist(userId);
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<DnsRegionalServerView> dnsAdminServerHealth() {
        log.info("Fetching DNS server health (admin)");
        return dnsService.getRegionalServers();
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<DnsQueryLogView> dnsAdminQueryLogs(
            @Argument Integer limit,
            @Argument String serviceId,
            @Argument String region) {
        log.info("Fetching DNS query logs (admin)");
        return dnsService.getAdminQueryLogs(
            limit != null ? limit : 100,
            serviceId,
            region
        );
    }
}
