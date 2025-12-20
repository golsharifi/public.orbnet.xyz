package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.OrbMeshSubscriptionValidationService;
import com.orbvpn.api.service.OrbMeshSubscriptionValidationService.ConnectionStatus;
import com.orbvpn.api.service.UserService;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * GraphQL Query Resolver for OrbMesh connection status.
 * Allows the app to check if a user can connect before attempting.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class OrbMeshConnectionStatusQueryResolver {

    private final OrbMeshSubscriptionValidationService validationService;
    private final UserService userService;

    /**
     * Get the current user's connection status.
     * Call this before attempting to connect to get user-friendly error messages.
     */
    @Secured(USER)
    @QueryMapping
    public Map<String, Object> orbmeshConnectionStatus() {
        User currentUser = userService.getUser();
        log.debug("User {} checking OrbMesh connection status", currentUser.getEmail());

        ConnectionStatus status = validationService.getConnectionStatus(currentUser);

        Map<String, Object> response = new HashMap<>();
        response.put("hasSubscription", status.isHasSubscription());
        response.put("subscriptionValid", status.isSubscriptionValid());
        response.put("subscriptionExpiresAt",
                status.getSubscriptionExpiresAt() != null
                        ? status.getSubscriptionExpiresAt().toString()
                        : null);
        response.put("deviceLimit", status.getDeviceLimit());
        response.put("activeConnections", status.getActiveConnections());
        response.put("remainingSlots", status.getRemainingSlots());
        response.put("canConnect", status.isCanConnect());
        response.put("message", status.getMessage());

        log.info("User {} connection status: canConnect={}, activeConnections={}/{}",
                currentUser.getEmail(),
                status.isCanConnect(),
                status.getActiveConnections(),
                status.getDeviceLimit());

        return response;
    }
}
