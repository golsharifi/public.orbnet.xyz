package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.WhatsAppStatus;
import com.orbvpn.api.service.notification.WhatsAppService;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.Base64;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WhatsAppMutationResolver {
    private final WhatsAppService whatsAppService;

    @Secured(ADMIN)
    @QueryMapping
    public WhatsAppStatus whatsAppStatus() {
        log.info("Fetching WhatsApp status");
        try {
            // Get the actual connection status and authentication status
            boolean authenticated = whatsAppService.isAuthenticated();
            boolean connected = whatsAppService.getConnectionState() != WhatsAppService.ConnectionState.DISCONNECTED;

            String qrCode = whatsAppService.getQrCode();
            String qrCodeSvg = whatsAppService.getQrCodeSvg();
            boolean expired = whatsAppService.isQrCodeExpired();
            int age = whatsAppService.getQrCodeAge();

            log.info(
                    "Raw service data - Authenticated: {}, Connected: {}, QR Code: {}, QR SVG: {}, Age: {}ms, Expired: {}",
                    authenticated, connected, qrCode != null, qrCodeSvg != null, age, expired);

            // FIXED LOGIC: Always show QR code if available and not authenticated
            // Don't hide QR code just because connected=true

            String displayQrCode = null;
            String displayQrCodeSvg = null;

            // Process QR codes if they exist and are not expired
            if ((qrCode != null || qrCodeSvg != null) && !expired) {
                log.info("Processing QR codes for display...");

                if (qrCode != null) {
                    // Check if it's already base64 encoded
                    if (qrCode.matches("^[A-Za-z0-9+/]+=*$") && qrCode.length() > 100) {
                        // Already base64 encoded
                        displayQrCode = qrCode;
                    } else {
                        // Raw QR data, need to encode
                        displayQrCode = Base64.getEncoder().encodeToString(qrCode.getBytes());
                    }
                    log.info("QR Code processed - Original length: {}, Display length: {}",
                            qrCode.length(), displayQrCode.length());
                }

                if (qrCodeSvg != null) {
                    // Check if SVG is already base64 encoded
                    if (qrCodeSvg.matches("^[A-Za-z0-9+/]+=*$")) {
                        // Already base64 encoded
                        displayQrCodeSvg = qrCodeSvg;
                    } else if (qrCodeSvg.startsWith("<?xml") || qrCodeSvg.startsWith("<svg")) {
                        // Raw SVG - encode for data URI
                        displayQrCodeSvg = Base64.getEncoder().encodeToString(qrCodeSvg.getBytes());
                    } else {
                        // Unknown format, assume it's already encoded
                        displayQrCodeSvg = qrCodeSvg;
                    }
                    log.info("QR SVG processed - Original length: {}, Display length: {}",
                            qrCodeSvg.length(), displayQrCodeSvg.length());
                }
            }

            // FIXED: Connection status logic
            // Return connected=true if:
            // 1. Authenticated (logged in) OR
            // 2. QR code is available for scanning (waiting for authentication)
            boolean statusConnected = authenticated || (displayQrCode != null || displayQrCodeSvg != null);

            WhatsAppStatus status = new WhatsAppStatus(
                    statusConnected,
                    displayQrCode,
                    displayQrCodeSvg,
                    age,
                    expired);

            log.info("FIXED LOGIC - Returning WhatsApp status:");
            log.info("  • Connected: {} (authenticated={} OR hasQR={})",
                    statusConnected, authenticated, (displayQrCode != null || displayQrCodeSvg != null));
            log.info("  • QR Available: {}", displayQrCode != null || displayQrCodeSvg != null);
            log.info("  • QR Valid: {}", (displayQrCode != null || displayQrCodeSvg != null) && !expired);
            log.info("  • QR Age: {}ms", age);
            log.info("  • Service State: {}", whatsAppService.getConnectionState());
            log.info("  • Authenticated: {}", authenticated);

            return status;

        } catch (Exception e) {
            log.error("Error fetching WhatsApp status - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public boolean whatsAppDisconnect() {
        log.info("Disconnecting WhatsApp");
        try {
            whatsAppService.disconnect();
            log.info("Successfully disconnected WhatsApp");
            return true;
        } catch (Exception e) {
            log.error("Error disconnecting WhatsApp - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Non-blocking WhatsApp connect mutation.
     * Triggers connection process and returns immediately.
     * Client should poll whatsAppStatus() to check for QR code availability.
     */
    @Secured(ADMIN)
    @MutationMapping
    public boolean whatsAppConnect() {
        log.info("WhatsApp connect mutation called");
        try {
            // Check if already authenticated
            if (whatsAppService.isAuthenticated()) {
                log.info("WhatsApp is already authenticated");
                return true;
            }

            // Check if we have a valid QR code available for scanning
            String currentQrCode = whatsAppService.getQrCode();
            if (currentQrCode != null && !whatsAppService.isQrCodeExpired()) {
                log.info("Valid QR code already available - Age: {}ms", whatsAppService.getQrCodeAge());

                // Refresh if QR is getting old (> 60 seconds)
                if (whatsAppService.getQrCodeAge() > 60000) {
                    log.info("QR code is old, triggering refresh asynchronously...");
                    whatsAppService.triggerQrCodeGeneration();
                }
                return true;
            }

            // Trigger QR code generation asynchronously - don't block
            log.info("Triggering QR code generation asynchronously...");
            whatsAppService.connect();

            // Return immediately - client should poll whatsAppStatus() for updates
            log.info("Connection process initiated. Poll whatsAppStatus() for QR code.");
            return true;

        } catch (Exception e) {
            log.error("Error connecting WhatsApp: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to connect WhatsApp: " + e.getMessage(), e);
        }
    }

    /**
     * Enhanced debug mutation with more detailed information
     */
    @Secured(ADMIN)
    @MutationMapping
    public String whatsAppDebugState() {
        log.info("Debug state requested");
        try {
            whatsAppService.debugWhatsAppState();

            StringBuilder debugInfo = new StringBuilder();
            debugInfo.append("=== WhatsApp Debug Information ===\n");
            debugInfo.append("Connection Status: ").append(whatsAppService.getConnectionStatus()).append("\n");
            debugInfo.append("Authentication Status: ").append(whatsAppService.isAuthenticated()).append("\n");
            debugInfo.append("Connection State: ").append(whatsAppService.getConnectionState()).append("\n");
            debugInfo.append("QR Code Available: ").append(whatsAppService.getQrCode() != null).append("\n");
            debugInfo.append("QR Code Age: ").append(whatsAppService.getQrCodeAge()).append("ms\n");
            debugInfo.append("QR Code Expired: ").append(whatsAppService.isQrCodeExpired()).append("\n");
            debugInfo.append("Service Running: ").append(whatsAppService.isRunning()).append("\n");
            debugInfo.append("Timestamp: ").append(System.currentTimeMillis()).append("\n");
            debugInfo.append("=====================================");

            String result = debugInfo.toString();
            log.info("Debug state result:\n{}", result);
            return result;

        } catch (Exception e) {
            log.error("Error getting debug state", e);
            String errorResult = "Error getting debug state: " + e.getMessage();
            return errorResult;
        }
    }

    /**
     * Non-blocking QR regeneration.
     * Triggers QR generation and returns immediately.
     * Client should poll whatsAppStatus() to get the new QR code.
     */
    @Secured(ADMIN)
    @MutationMapping
    public boolean whatsAppRegenerateQR() {
        log.info("QR regeneration requested");
        try {
            // Check if already authenticated
            if (whatsAppService.isAuthenticated()) {
                log.info("Already authenticated, no QR code needed");
                return true;
            }

            // Trigger regeneration asynchronously - don't block
            whatsAppService.triggerQrCodeGeneration();

            log.info("QR regeneration initiated. Poll whatsAppStatus() for the new QR code.");
            return true;

        } catch (Exception e) {
            log.error("Error regenerating QR code: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Non-blocking force reset of WhatsApp instance.
     * Triggers reset and returns immediately.
     * Client should poll whatsAppStatus() for updates.
     */
    @Secured(ADMIN)
    @MutationMapping
    public boolean whatsAppForceReset() {
        log.info("Force reset of WhatsApp instance requested");
        try {
            // Trigger force recreate asynchronously - don't block
            whatsAppService.forceRecreateInstance();

            log.info("Force reset initiated. Poll whatsAppStatus() for updates.");
            return true;

        } catch (Exception e) {
            log.error("Error during force reset: {}", e.getMessage(), e);
            throw new RuntimeException("Force reset failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get detailed WhatsApp service metrics
     */
    @Secured(ADMIN)
    @QueryMapping
    public String whatsAppMetrics() {
        try {
            StringBuilder metrics = new StringBuilder();
            metrics.append("=== WhatsApp Service Metrics ===\n");
            metrics.append("Uptime: ").append(whatsAppService.isRunning() ? "Running" : "Stopped").append("\n");
            metrics.append("Authentication: ").append(whatsAppService.isAuthenticated()).append("\n");
            metrics.append("Connection State: ").append(whatsAppService.getConnectionState()).append("\n");

            String qrCode = whatsAppService.getQrCode();
            if (qrCode != null) {
                metrics.append("QR Code: Available (").append(qrCode.length()).append(" chars)\n");
                metrics.append("QR Age: ").append(whatsAppService.getQrCodeAge()).append("ms\n");
                metrics.append("QR Expired: ").append(whatsAppService.isQrCodeExpired()).append("\n");
            } else {
                metrics.append("QR Code: Not Available\n");
            }

            metrics.append("Service Status: ").append(whatsAppService.getConnectionStatus()).append("\n");
            metrics.append("Timestamp: ").append(System.currentTimeMillis()).append("\n");
            metrics.append("===============================");

            return metrics.toString();

        } catch (Exception e) {
            log.error("Error getting WhatsApp metrics", e);
            return "Error getting metrics: " + e.getMessage();
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public String whatsAppDebugAuthentication() {
        log.info("Debug authentication details requested");
        try {
            whatsAppService.debugAuthenticationDetails();
            return "Debug authentication completed - check logs for details";
        } catch (Exception e) {
            log.error("Error in debug authentication", e);
            return "Error in debug: " + e.getMessage();
        }
    }
}