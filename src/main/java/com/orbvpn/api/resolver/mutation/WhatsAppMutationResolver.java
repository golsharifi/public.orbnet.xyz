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
     * Enhanced WhatsApp connect mutation with better error handling and feedback
     */
    @Secured(ADMIN)
    @MutationMapping
    public boolean whatsAppConnect() {
        log.info("🚀 WhatsApp connect mutation called");
        try {
            // Debug current state
            whatsAppService.debugWhatsAppState();

            // Check if already authenticated (not just connected)
            if (whatsAppService.isAuthenticated()) {
                log.info("WhatsApp is already authenticated");
                return true;
            }

            // Check if we have a valid QR code available for scanning
            String currentQrCode = whatsAppService.getQrCode();
            if (currentQrCode != null && !whatsAppService.isQrCodeExpired()) {
                log.info("Valid QR code already available for scanning - QR Age: {}ms",
                        whatsAppService.getQrCodeAge());

                // Check if QR is getting old and needs refresh
                if (whatsAppService.getQrCodeAge() > 60000) { // 60 seconds
                    log.info("QR code is getting old, triggering refresh...");
                    whatsAppService.triggerQrCodeGeneration();
                }
                return true;
            }

            // Need to generate new QR code
            if (currentQrCode == null) {
                log.info("No QR code available, triggering generation...");
            } else {
                log.info("QR code expired (Age: {}ms), triggering new generation...",
                        whatsAppService.getQrCodeAge());
            }

            // Trigger QR code generation using the improved connect method
            whatsAppService.connect();

            // Wait for the process to start with progressive checks
            boolean qrGenerated = false;
            int maxWaitSeconds = 20; // Increased wait time

            for (int i = 0; i < maxWaitSeconds; i++) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Sleep interrupted during connect wait");
                    break;
                }

                // Check for QR code availability
                String newQrCode = whatsAppService.getQrCode();
                if (newQrCode != null && !whatsAppService.isQrCodeExpired()) {
                    log.info("✅ QR code generated successfully after {}s", i + 1);
                    qrGenerated = true;
                    break;
                }

                // Also check if we became authenticated during the wait
                if (whatsAppService.isAuthenticated()) {
                    log.info("✅ WhatsApp became authenticated during connection process");
                    qrGenerated = true;
                    break;
                }

                // Log progress every 3 seconds
                if (i % 3 == 0 && i > 0) {
                    log.info("Still waiting for QR generation or authentication... ({}s elapsed)", i + 1);
                }
            }

            if (!qrGenerated) {
                log.warn("⚠️ QR code generation/authentication taking longer than expected");
                log.info("Final state check after {}s wait:", maxWaitSeconds);
                whatsAppService.debugWhatsAppState();
            }

            log.info("✅ Successfully initiated WhatsApp connection process");
            return true;

        } catch (Exception e) {
            log.error("❌ Error connecting WhatsApp - Error: {}", e.getMessage(), e);
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
     * Enhanced QR regeneration with better feedback
     */
    @Secured(ADMIN)
    @MutationMapping
    public boolean whatsAppRegenerateQR() {
        log.info("Force QR regeneration requested");
        try {
            // Log current state before regeneration
            log.info("Current state before regeneration:");
            whatsAppService.debugWhatsAppState();

            // Check if we're already authenticated
            if (whatsAppService.isAuthenticated()) {
                log.info("Already authenticated, no QR code needed");
                return true;
            }

            // Force regeneration
            whatsAppService.triggerQrCodeGeneration();

            // Wait for generation with progressive checks
            boolean qrGenerated = false;
            int maxWaitSeconds = 15;

            for (int i = 0; i < maxWaitSeconds; i++) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Sleep interrupted during QR regeneration wait");
                    break;
                }

                String qrCode = whatsAppService.getQrCode();
                if (qrCode != null && !whatsAppService.isQrCodeExpired()) {
                    log.info("✅ QR code generated successfully after {}s", i + 1);
                    qrGenerated = true;
                    break;
                }

                if (i % 3 == 0) { // Log every 3 seconds
                    log.info("Still waiting for QR generation... ({}s elapsed)", i + 1);
                }
            }

            if (!qrGenerated) {
                log.warn("⚠️ QR code regeneration taking longer than expected");
                // Log final state
                whatsAppService.debugWhatsAppState();
            }

            return qrGenerated;

        } catch (Exception e) {
            log.error("Error regenerating QR code", e);
            throw e;
        }
    }

    /**
     * Force complete reset of WhatsApp instance
     */
    @Secured(ADMIN)
    @MutationMapping
    public boolean whatsAppForceReset() {
        log.info("Force reset of WhatsApp instance requested");
        try {
            log.info("Current state before reset:");
            whatsAppService.debugWhatsAppState();

            // Use the force recreate method
            whatsAppService.forceRecreateInstance();

            // Wait for the reset to complete
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Sleep interrupted during force reset");
                return false;
            }

            log.info("State after reset:");
            whatsAppService.debugWhatsAppState();

            // Check if QR code is available after reset
            String qrCode = whatsAppService.getQrCode();
            boolean success = qrCode != null || whatsAppService.isAuthenticated();

            if (success) {
                log.info("✅ Force reset completed successfully");
            } else {
                log.warn("⚠️ Force reset completed but may need more time for QR generation");
            }

            return success;

        } catch (Exception e) {
            log.error("Error during force reset", e);
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

    /**
     * TEST VERSION: Always returns QR code data for testing HTML display
     * This bypasses authentication checks to test QR display functionality
     */
    @Secured(ADMIN)
    @QueryMapping
    public WhatsAppStatus whatsAppStatusTest() {
        log.info("Fetching WhatsApp status (TEST VERSION - ignores authentication)");
        try {
            // Get QR code data regardless of authentication status
            String qrCode = whatsAppService.getQrCode();
            String qrCodeSvg = whatsAppService.getQrCodeSvg();
            boolean expired = whatsAppService.isQrCodeExpired();
            int age = whatsAppService.getQrCodeAge();
            boolean authenticated = whatsAppService.isAuthenticated();

            log.info("TEST - Raw service data: QR Code: {}, QR SVG: {}, Age: {}ms, Authenticated: {}",
                    qrCode != null, qrCodeSvg != null, age, authenticated);

            // Always process QR data if available (ignore authentication for testing)
            String displayQrCode = null;
            String displayQrCodeSvg = null;

            if (qrCode != null) {
                if (qrCode.matches("^[A-Za-z0-9+/]+=*$") && qrCode.length() > 100) {
                    displayQrCode = qrCode;
                } else {
                    displayQrCode = Base64.getEncoder().encodeToString(qrCode.getBytes());
                }
                log.info("TEST - QR Code processed: Original={}, Display={}", qrCode.length(), displayQrCode.length());
            }

            if (qrCodeSvg != null) {
                if (qrCodeSvg.matches("^[A-Za-z0-9+/]+=*$")) {
                    displayQrCodeSvg = qrCodeSvg;
                } else if (qrCodeSvg.startsWith("<?xml") || qrCodeSvg.startsWith("<svg")) {
                    displayQrCodeSvg = Base64.getEncoder().encodeToString(qrCodeSvg.getBytes());
                } else {
                    displayQrCodeSvg = qrCodeSvg;
                }
                log.info("TEST - QR SVG processed: Original={}, Display={}", qrCodeSvg.length(),
                        displayQrCodeSvg.length());
            }

            // Return QR data regardless of authentication (for testing)
            boolean hasQrData = displayQrCode != null || displayQrCodeSvg != null;

            WhatsAppStatus status = new WhatsAppStatus(
                    hasQrData, // connected = true if we have QR data
                    displayQrCode,
                    displayQrCodeSvg,
                    age,
                    expired);

            log.info("TEST - Returning status: Connected={}, QR Available={}, Age={}ms",
                    hasQrData, hasQrData, age);

            return status;

        } catch (Exception e) {
            log.error("Error in TEST whatsAppStatus: {}", e.getMessage(), e);
            throw e;
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