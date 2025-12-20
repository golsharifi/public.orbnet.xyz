package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.DnsServiceType;
import com.orbvpn.api.service.DnsService;
import com.orbvpn.api.service.GeolocationService;
import com.orbvpn.api.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import jakarta.validation.Valid;

import static com.orbvpn.api.domain.enums.RoleName.Constants.*;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DnsMutationResolver {

    private final DnsService dnsService;
    private final UserService userService;
    private final GeolocationService geolocationService;

    // ========================================================================
    // USER MUTATIONS - SERVICE RULES
    // ========================================================================

    @Secured(USER)
    @MutationMapping
    public DnsUserRuleView setDnsServiceRule(@Argument @Valid DnsServiceRuleInput input) {
        User user = userService.getUser();
        log.info("Setting DNS rule for user: {}, service: {}, enabled: {}",
            user.getId(), input.getServiceId(), input.isEnabled());
        return dnsService.setUserRule(user, input);
    }

    @Secured(USER)
    @MutationMapping
    public boolean removeDnsServiceRule(
            @Argument String serviceId,
            @Argument DnsServiceType serviceType) {
        User user = userService.getUser();
        log.info("Removing DNS rule for user: {}, service: {}", user.getId(), serviceId);
        return dnsService.removeUserRule(user, serviceId, serviceType);
    }

    @Secured(USER)
    @MutationMapping
    public int enableAllDnsServices(@Argument DnsServiceType serviceType) {
        User user = userService.getUser();
        log.info("Enabling all {} DNS services for user: {}", serviceType, user.getId());
        return dnsService.enableAllServices(user, serviceType);
    }

    @Secured(USER)
    @MutationMapping
    public int disableAllDnsServices(@Argument DnsServiceType serviceType) {
        User user = userService.getUser();
        log.info("Disabling all {} DNS services for user: {}", serviceType, user.getId());
        return dnsService.disableAllServices(user, serviceType);
    }

    @Secured(USER)
    @MutationMapping
    public boolean resetDnsRulesToDefault() {
        User user = userService.getUser();
        log.info("Resetting DNS rules to default for user: {}", user.getId());
        return dnsService.resetUserRulesToDefault(user);
    }

    // ========================================================================
    // USER MUTATIONS - IP WHITELIST
    // ========================================================================

    @Secured(USER)
    @MutationMapping
    public DnsWhitelistedIpView whitelistDnsIp(@Argument @Valid DnsWhitelistIpInput input) {
        User user = userService.getUser();
        log.info("Whitelisting IP for user: {}, IP: {}", user.getId(), input.getIpAddress());
        return dnsService.whitelistIp(user, input);
    }

    @Secured(USER)
    @MutationMapping
    public DnsWhitelistedIpView updateWhitelistedIp(
            @Argument Long id,
            @Argument String label,
            @Argument Boolean active) {
        User user = userService.getUser();
        log.info("Updating whitelisted IP {} for user: {}", id, user.getId());
        return dnsService.updateWhitelistedIp(user, id, label, active);
    }

    @Secured(USER)
    @MutationMapping
    public boolean removeWhitelistedIp(@Argument Long id) {
        User user = userService.getUser();
        log.info("Removing whitelisted IP {} for user: {}", id, user.getId());
        return dnsService.removeWhitelistedIp(user, id);
    }

    @Secured(USER)
    @MutationMapping
    public DnsWhitelistedIpView whitelistCurrentIp(
            @Argument String label,
            @Argument String deviceType) {
        User user = userService.getUser();
        String currentIp = geolocationService.getCurrentUserIP();
        log.info("Whitelisting current IP {} for user: {}", currentIp, user.getId());
        return dnsService.whitelistCurrentIp(user, label, deviceType, currentIp);
    }

    // ========================================================================
    // USER MUTATIONS - QUICK ACTIONS
    // ========================================================================

    @Secured(USER)
    @MutationMapping
    public boolean enableDns() {
        User user = userService.getUser();
        log.info("Enabling DNS for user: {}", user.getId());
        // Enable all streaming services by default
        dnsService.enableAllServices(user, DnsServiceType.STREAMING);
        return true;
    }

    @Secured(USER)
    @MutationMapping
    public boolean disableDns() {
        User user = userService.getUser();
        log.info("Disabling DNS for user: {}", user.getId());
        // Disable all services
        dnsService.disableAllServices(user, DnsServiceType.STREAMING);
        dnsService.disableAllServices(user, DnsServiceType.REGIONAL);
        return true;
    }

    // ========================================================================
    // ADMIN MUTATIONS
    // ========================================================================

    @Secured(ADMIN)
    @MutationMapping
    public DnsUserRuleView adminSetDnsUserRule(@Argument @Valid DnsAdminUserRuleInput input) {
        log.info("Admin setting DNS rule for user: {}, service: {}",
            input.getUserId(), input.getServiceId());
        return dnsService.adminSetUserRule(input);
    }

    @Secured(ADMIN)
    @MutationMapping
    public boolean adminRemoveDnsUserRule(
            @Argument int userId,
            @Argument String serviceId,
            @Argument DnsServiceType serviceType) {
        log.info("Admin removing DNS rule for user: {}, service: {}", userId, serviceId);
        User user = userService.getUserById(userId);
        return dnsService.removeUserRule(user, serviceId, serviceType);
    }

    @Secured(ADMIN)
    @MutationMapping
    public DnsWhitelistedIpView adminWhitelistUserIp(@Argument @Valid DnsAdminWhitelistInput input) {
        log.info("Admin whitelisting IP for user: {}, IP: {}",
            input.getUserId(), input.getIpAddress());
        return dnsService.adminWhitelistUserIp(input);
    }

    @Secured(ADMIN)
    @MutationMapping
    public boolean adminRemoveUserWhitelistedIp(
            @Argument int userId,
            @Argument Long ipId) {
        log.info("Admin removing whitelisted IP {} for user: {}", ipId, userId);
        User user = userService.getUserById(userId);
        return dnsService.removeWhitelistedIp(user, ipId);
    }

    @Secured(ADMIN)
    @MutationMapping
    public java.util.List<DnsRegionalServerView> adminRefreshServerHealth() {
        log.info("Admin refreshing DNS server health");
        // TODO: Implement actual health check against Go servers
        return dnsService.getRegionalServers();
    }

    @Secured(ADMIN)
    @MutationMapping
    public boolean adminPurgeDnsCache() {
        log.info("Admin purging DNS cache");
        // TODO: Implement cache purge via Go server API
        return true;
    }

    @Secured(ADMIN)
    @MutationMapping
    public DnsConfigView adminUpdateDnsConfig(
            @Argument Boolean enabled,
            @Argument Boolean dohEnabled,
            @Argument Boolean sniProxyEnabled,
            @Argument Integer maxWhitelistedIps,
            @Argument Integer whitelistExpiryDays) {
        log.info("Admin updating DNS config");
        return dnsService.updateConfig(enabled, dohEnabled, sniProxyEnabled,
            maxWhitelistedIps, whitelistExpiryDays);
    }
}
