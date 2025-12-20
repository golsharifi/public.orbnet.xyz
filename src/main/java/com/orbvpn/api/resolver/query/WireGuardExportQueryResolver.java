package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.WireGuardConfigService;
import com.orbvpn.api.service.WireGuardConfigService.WireGuardExportData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WireGuardExportQueryResolver {

    private final WireGuardConfigService wireGuardConfigService;
    private final UserService userService;
    private final UserRepository userRepository;

    /**
     * Check if third-party WireGuard clients are allowed
     */
    @QueryMapping
    public Map<String, Object> wireGuardExportStatus() {
        boolean allowed = wireGuardConfigService.isThirdPartyClientsAllowed();
        return Map.of(
                "allowed", allowed,
                "message", allowed
                        ? "Third-party WireGuard clients are enabled"
                        : "Third-party WireGuard clients are disabled by the administrator"
        );
    }

    /**
     * Get WireGuard config export for a specific config
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public WireGuardExportData getWireGuardExport(@Argument Long configId) {
        User user = userService.getUser();
        log.info("User {} requesting WireGuard export for config {}", user.getEmail(), configId);
        return wireGuardConfigService.getConfigExport(user, configId);
    }

    /**
     * Get all WireGuard config exports for current user
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<WireGuardExportData> myWireGuardExports() {
        User user = userService.getUser();
        log.info("User {} requesting all WireGuard exports", user.getEmail());
        return wireGuardConfigService.getAllConfigExports(user);
    }

    /**
     * Admin: Get WireGuard exports for a specific user
     */
    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public List<WireGuardExportData> getUserWireGuardExports(@Argument Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        log.info("Admin requesting WireGuard exports for user {}", user.getEmail());
        return wireGuardConfigService.getConfigExportsForUser(user.getUuid());
    }
}
