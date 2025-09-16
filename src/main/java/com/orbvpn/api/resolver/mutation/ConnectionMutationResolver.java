package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.DeviceIdInput;
import com.orbvpn.api.service.ConnectionService;
import com.orbvpn.api.service.DeviceService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ConnectionMutationResolver {
    private final ConnectionService connectionService;
    private final DeviceService deviceService;

    @Secured(ADMIN)
    @MutationMapping
    public Boolean disconnectBySessionId(
            @Argument @Valid @NotBlank(message = "Session ID cannot be empty") String onlineSessionId) {
        log.info("Disconnecting session: {}", onlineSessionId);
        try {
            Boolean result = connectionService.disconnect(onlineSessionId);
            log.info("Successfully disconnected session: {}", onlineSessionId);
            return result;
        } catch (Exception e) {
            log.error("Error disconnecting session: {} - Error: {}", onlineSessionId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean disconnectByUserIdAndDeviceId(
            @Argument @Valid @Positive(message = "User ID must be positive") Integer userId,
            @Argument @Valid DeviceIdInput deviceIdInput) {
        log.info("Disconnecting device for user: {}", userId);
        try {
            Boolean result = connectionService.disconnect(userId, deviceIdInput);
            log.info("Successfully disconnected device for user: {}", userId);
            return result;
        } catch (Exception e) {
            log.error("Error disconnecting device for user: {} - Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean activateDevice(
            @Argument @Valid @Positive(message = "User ID must be positive") Integer userId,
            @Argument @Valid DeviceIdInput deviceIdInput) {
        log.info("Activating device for user: {}", userId);
        try {
            Boolean result = deviceService.activateDevice(userId, deviceIdInput);
            log.info("Successfully activated device for user: {}", userId);
            return result;
        } catch (Exception e) {
            log.error("Error activating device for user: {} - Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean deactivateDevice(
            @Argument @Valid @Positive(message = "User ID must be positive") Integer userId,
            @Argument @Valid DeviceIdInput deviceIdInput) {
        log.info("Deactivating device for user: {}", userId);
        try {
            Boolean result = deviceService.deactivateDevice(userId, deviceIdInput);
            log.info("Successfully deactivated device for user: {}", userId);
            return result;
        } catch (Exception e) {
            log.error("Error deactivating device for user: {} - Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }
}