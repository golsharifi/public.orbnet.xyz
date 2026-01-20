package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.config.security.Unsecured;
import com.orbvpn.api.domain.dto.AuthenticatedUser;
import com.orbvpn.api.domain.dto.QrLoginResponse;
import com.orbvpn.api.service.QrLoginService;
import com.orbvpn.api.service.UserService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;
import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.RESELLER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class QrLoginMutationResolver {
    private final QrLoginService qrLoginService;
    private final UserService userService;

    @Unsecured
    @MutationMapping
    public QrLoginResponse generateQrCode() {
        log.info("Generating QR code");
        try {
            return qrLoginService.generateQrCode();
        } catch (Exception e) {
            log.error("Error generating QR code - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Unsecured
    @MutationMapping
    public QrLoginResponse refreshQrCode(
            @Argument @Valid @NotBlank(message = "Session ID cannot be empty") String sessionId) {
        log.info("Refreshing QR code for session: {}", sessionId);
        try {
            return qrLoginService.refreshQrCode(sessionId);
        } catch (Exception e) {
            log.error("Error refreshing QR code - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @MutationMapping
    public Boolean confirmQrLogin(
            @Argument @Valid @NotBlank(message = "QR code cannot be empty") String qrCode) {
        log.info("Confirming QR login");
        try {
            qrLoginService.confirmQrLogin(qrCode, userService.getUser());
            return true;
        } catch (Exception e) {
            log.error("Error confirming QR login - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Unsecured
    @MutationMapping
    public AuthenticatedUser checkQrLoginStatus(
            @Argument @Valid @NotBlank(message = "Session ID cannot be empty") String sessionId) {
        log.info("Checking QR login status for session: {}", sessionId);
        try {
            return qrLoginService.checkQrLoginStatus(sessionId);
        } catch (Exception e) {
            log.error("Error checking QR login status - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Generate a direct login QR code for the authenticated user.
     * This QR code contains tokens and can be scanned by another device
     * to log in directly without the polling flow.
     *
     * Use case: User logged in on web goes to Settings -> QR Login,
     * mobile app (not logged in) scans to get logged in instantly.
     *
     * Accessible by: USER, ADMIN, and RESELLER roles.
     */
    @Secured({USER, ADMIN, RESELLER})
    @MutationMapping
    public QrLoginResponse generateDirectLoginQr() {
        log.info("Generating direct login QR code");
        try {
            return qrLoginService.generateDirectLoginQr(userService.getUser());
        } catch (Exception e) {
            log.error("Error generating direct login QR - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}