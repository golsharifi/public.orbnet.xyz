package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.exception.AccessDeniedException;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.ConnectionService;
import com.orbvpn.api.service.DeviceService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.data.domain.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Email;
import com.orbvpn.api.domain.enums.RoleName;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ConnectionQueryResolver {
    private final ConnectionService connectionService;
    private final DeviceService deviceService;
    private final UserService userService;

    @Secured(ADMIN)
    @QueryMapping
    public List<ConnectionHistoryView> getConnectionHistory(
            @Argument @Valid @Positive(message = "User ID must be positive") Integer userId) {
        log.info("Fetching connection history for user: {}", userId);
        try {
            List<ConnectionHistoryView> history = connectionService.getConnectionHistory(userId);
            log.info("Successfully retrieved {} connection history records for user: {}", history.size(), userId);
            return history;
        } catch (Exception e) {
            log.error("Error fetching connection history for user: {} - Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<OnlineSessionView> getOnlineSessions(
            @Argument @Valid @Positive(message = "User ID must be positive") Integer userId) {
        log.info("Fetching online sessions for user: {}", userId);
        try {
            List<OnlineSessionView> sessions = connectionService.getOnlineSessions(userId);
            log.info("Successfully retrieved {} online sessions for user: {}", sessions.size(), userId);
            return sessions;
        } catch (Exception e) {
            log.error("Error fetching online sessions for user: {} - Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public Page<UserView> getOnlineUsers(
            @Argument Integer page,
            @Argument Integer size,
            @Argument Integer serverId,
            @Argument Integer groupId,
            @Argument Integer roleId,
            @Argument Integer serviceGroupId) {
        log.info("Fetching online users - page: {}, size: {}", page, size);
        try {
            Page<UserView> users = connectionService.getOnlineUsers(page, size, serverId, groupId, roleId,
                    serviceGroupId);
            log.info("Successfully retrieved page {} of online users with {} entries", users.getNumber(),
                    users.getNumberOfElements());
            return users;
        } catch (Exception e) {
            log.error("Error fetching online users - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ "ADMIN", "USER" })
    @QueryMapping
    public List<DeviceView> getDevices(
            @Argument @Valid @Positive(message = "User ID must be positive") Integer userId) {
        log.info("Fetching devices for user: {}", userId);
        try {
            // Get current user
            User currentUser = userService.getUser();

            // Check if user has access
            if (currentUser.getRole().getName() != RoleName.ADMIN
                    && !userId.equals(currentUser.getId())) {
                String message = String.format("User %d cannot access devices of user %d",
                        currentUser.getId(), userId);
                log.warn("Access denied: {}", message);
                throw new AccessDeniedException(message);
            }

            List<DeviceView> devices = deviceService.getDevices(userId);
            log.info("Successfully retrieved {} devices for user: {}", devices.size(), userId);
            return devices;
        } catch (AccessDeniedException e) {
            log.warn("Access denied while fetching devices - Error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error fetching devices for user: {} - Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<DeviceView> getDevicesByEmail(
            @Argument @Valid @Email(message = "Invalid email format") String email) {
        log.info("Fetching devices for email: {}", email);
        try {
            List<DeviceView> devices = deviceService.getDevicesByEmail(email);
            log.info("Successfully retrieved {} devices for email: {}", devices.size(), email);
            return devices;
        } catch (Exception e) {
            log.error("Error fetching devices for email: {} - Error: {}", email, e.getMessage(), e);
            throw e;
        }
    }
}