package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.config.security.JwtTokenUtil;
import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.exception.BadRequestException;
import com.orbvpn.api.service.TokenBlacklistService;
import com.orbvpn.api.service.UserDeviceService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.RESELLER;

import java.util.HashMap;

@Slf4j
@Controller
@RequiredArgsConstructor
public class UserDeviceMutation {
    private final UserDeviceService userDeviceService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtTokenUtil jwtTokenUtil;
    private final HttpServletRequest httpServletRequest;

    @MutationMapping
    public UserDeviceView loginDevice(@Argument("device") @Valid UserDeviceDto userDeviceDto) {
        log.info("Processing device login request with deviceId: {}",
                userDeviceDto != null ? userDeviceDto.getDeviceId() : "null");
        try {
            if (userDeviceDto == null) {
                throw new BadRequestException("Device information cannot be null");
            }

            // Only deviceId is required as per GraphQL schema
            if (userDeviceDto.getDeviceId() == null || userDeviceDto.getDeviceId().trim().isEmpty()) {
                throw new BadRequestException("DeviceId cannot be empty");
            }

            return userDeviceService.loginDevice(userDeviceDto);
        } catch (Exception e) {
            log.error("Error processing device login - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Logout and revoke the current access token.
     * Optionally logout a specific device.
     */
    @MutationMapping
    public Boolean logout(@Argument String deviceId) {
        log.info("Processing logout request, deviceId: {}", deviceId);
        try {
            // Get current user
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
                log.warn("Logout called without valid authentication");
                return false;
            }
            User user = (User) authentication.getPrincipal();

            // Extract and revoke the current access token
            String authHeader = httpServletRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    tokenBlacklistService.revokeToken(
                        token,
                        user.getId(),
                        user.getUsername(),
                        jwtTokenUtil.getTokenType(token),
                        jwtTokenUtil.getExpirationDate(token),
                        "USER_LOGOUT",
                        httpServletRequest.getRemoteAddr()
                    );
                    log.info("Access token revoked for user: {}", user.getUsername());
                } catch (Exception e) {
                    log.warn("Could not revoke token: {}", e.getMessage());
                }
            }

            // Optionally logout the device
            if (deviceId != null && !deviceId.trim().isEmpty()) {
                try {
                    userDeviceService.logoutDevice(deviceId);
                    log.info("Device {} logged out for user: {}", deviceId, user.getUsername());
                } catch (Exception e) {
                    log.warn("Could not logout device {}: {}", deviceId, e.getMessage());
                }
            }

            return true;
        } catch (Exception e) {
            log.error("Error processing logout - Error: {}", e.getMessage(), e);
            return false;
        }
    }

    @MutationMapping
    public UserDeviceView logoutDeviceByUserDeviceId(
            @Argument @Valid Long userDeviceId) {
        log.info("Logging out device by ID: {}", userDeviceId);
        try {
            return userDeviceService.logoutDevice(userDeviceId);
        } catch (Exception e) {
            log.error("Error logging out device: {} - Error: {}", userDeviceId, e.getMessage(), e);
            throw e;
        }
    }

    @MutationMapping
    public UserDeviceView logoutDeviceByDeviceId(
            @Argument @Valid @NotBlank(message = "Device ID cannot be empty") String deviceId) {
        log.info("Logging out device: {}", deviceId);
        try {
            return userDeviceService.logoutDevice(deviceId);
        } catch (Exception e) {
            log.error("Error logging out device: {} - Error: {}", deviceId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public UserDeviceView resellerLogoutDevice(
            @Argument @Valid Integer userId,
            @Argument @Valid @NotBlank(message = "Device ID cannot be empty") String deviceId) {
        log.info("Reseller logging out device: {} for user: {}", deviceId, userId);
        try {
            return userDeviceService.resellerLogoutDevice(userId, deviceId);
        } catch (Exception e) {
            log.error("Error in reseller logout - User: {}, Device: {} - Error: {}",
                    userId, deviceId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public UserDeviceView resellerDeactivateDevice(
            @Argument @Valid @NotBlank(message = "Device ID cannot be empty") String deviceId) {
        log.info("Deactivating device: {}", deviceId);
        try {
            return userDeviceService.blockDevice(deviceId);
        } catch (Exception e) {
            log.error("Error deactivating device: {} - Error: {}", deviceId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public UserDeviceView resellerActivateDevice(
            @Argument @Valid @NotBlank(message = "Device ID cannot be empty") String deviceId) {
        log.info("Activating device: {}", deviceId);
        try {
            return userDeviceService.unblockDevice(deviceId);
        } catch (Exception e) {
            log.error("Error activating device: {} - Error: {}", deviceId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public FcmNotificationDto sendNotificationByDeviceId(
            @Argument String deviceId,
            @Argument NotificationInput notification) {
        log.info("Sending notification to device: {}", deviceId);

        NotificationDto notificationDto = NotificationDto.builder()
                .subject(notification.getSubject())
                .content(notification.getContent())
                .build();

        return userDeviceService.sendNotificationByDeviceId(deviceId, notificationDto);
    }

    @Secured(ADMIN)
    @MutationMapping
    public FcmNotificationDto sendNotificationByToken(
            @Argument @Valid @NotBlank(message = "Token cannot be empty") String token,
            @Argument @Valid NotificationInput notification) {
        log.info("Sending notification using token");
        try {
            NotificationDto notificationDto = NotificationDto.builder()
                    .subject(notification.getSubject())
                    .content(notification.getContent())
                    .data(new HashMap<>())
                    .build();
            return userDeviceService.adminSendNotificationByToken(token, notificationDto);
        } catch (Exception e) {
            log.error("Error sending notification with token - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public FcmNotificationDto sendNotificationToAll(@Argument NotificationInput notification) {
        log.info("Sending notification to all devices");
        try {
            NotificationDto notificationDto = NotificationDto.builder()
                    .subject(notification.getSubject())
                    .content(notification.getContent())
                    .data(new HashMap<>())
                    .build();
            return userDeviceService.adminSendNotificationToAll(notificationDto);
        } catch (Exception e) {
            log.error("Error sending notification to all devices - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}