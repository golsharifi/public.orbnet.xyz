package com.orbvpn.api.config.messaging;

import it.auties.whatsapp.api.Whatsapp;
import it.auties.whatsapp.api.QrHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.orbvpn.api.event.WhatsAppQRCodeEvent;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Configuration
public class CobaltConfig {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Value("${whatsapp.clean-session:false}")
    private boolean cleanSessionOnStartup;

    @Value("${whatsapp.auto-reconnect:true}")
    private boolean autoReconnect;

    private final AtomicBoolean qrHandlerActive = new AtomicBoolean(false);
    private volatile UUID currentSessionId;
    private volatile Whatsapp whatsappInstance;

    @Bean
    public Whatsapp whatsapp() {
        // Only clean sessions if explicitly configured to do so
        if (cleanSessionOnStartup) {
            log.info("Cleaning sessions on startup as configured");
            cleanSessionFiles();
        } else {
            log.info("Preserving existing sessions for authentication persistence");
        }

        log.info("Creating new WhatsApp instance...");

        // Generate a unique session ID
        currentSessionId = UUID.randomUUID();
        log.info("Using session UUID: {}", currentSessionId);

        // Create WhatsApp instance using the correct Cobalt API
        whatsappInstance = Whatsapp.webBuilder()
                .newConnection(currentSessionId)
                .unregistered(this::handleQrCode)
                .addLoggedInListener(this::handleLoginSuccess)
                .addDisconnectedListener(this::handleDisconnect)
                .connect() // This returns CompletableFuture<Whatsapp>
                .join(); // Wait for completion and get the Whatsapp instance

        log.info("WhatsApp instance created successfully. Ready for use.");

        return whatsappInstance;
    }

    /**
     * Enhanced QR code handler with proper event publishing
     */
    private void handleQrCode(String qr) {
        // Prevent multiple QR handlers from running simultaneously
        if (!qrHandlerActive.compareAndSet(false, true)) {
            log.warn("QR handler already active, ignoring duplicate QR generation");
            return;
        }

        try {
            log.info("🔥 QR CODE GENERATED! 🔥");
            log.info("Session ID: {}", currentSessionId);
            log.info("QR code length: {}", qr.length());
            log.info("Timestamp: {}", System.currentTimeMillis());

            // Display in terminal for debugging (non-blocking)
            CompletableFuture.runAsync(() -> {
                try {
                    QrHandler.toTerminal().accept(qr);
                } catch (Exception e) {
                    log.warn("Could not display QR in terminal: {}", e.getMessage());
                }
            });

            // Publish event immediately
            try {
                log.info("🎯 Publishing QR code event...");
                WhatsAppQRCodeEvent event = new WhatsAppQRCodeEvent(this, qr);
                eventPublisher.publishEvent(event);
                log.info("✅ QR code event published successfully");
            } catch (Exception e) {
                log.error("❌ Failed to publish QR code event: {}", e.getMessage(), e);
            }

            // Also try direct service update as fallback
            storeQrCodeWithRetry(qr, 3);

        } catch (Exception e) {
            log.error("❌ Critical error in QR handler: {}", e.getMessage(), e);
        } finally {
            // Reset flag after a delay to prevent rapid re-triggering
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(5000); // 5 second cooldown
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                qrHandlerActive.set(false);
                log.debug("QR handler cooldown completed");
            });
        }
    }

    /**
     * Store QR code with retry mechanism
     */
    private void storeQrCodeWithRetry(String qr, int maxRetries) {
        CompletableFuture.runAsync(() -> {
            int attempts = 0;
            boolean success = false;

            while (attempts < maxRetries && !success) {
                attempts++;
                try {
                    // Brief delay to ensure context is ready
                    if (attempts == 1) {
                        Thread.sleep(1000); // First attempt - wait for context
                    } else {
                        Thread.sleep(2000); // Retry attempts - longer wait
                    }

                    log.info("🎯 Storing QR code in WhatsApp service (attempt {}/{})", attempts, maxRetries);

                    var whatsAppService = applicationContext.getBean(
                            "whatsAppService",
                            com.orbvpn.api.service.notification.WhatsAppService.class);

                    if (whatsAppService != null) {
                        whatsAppService.updateQrCode(qr);
                        log.info("✅ QR code stored successfully on attempt {}", attempts);
                        success = true;
                    } else {
                        log.warn("⚠️ WhatsAppService not found in context (attempt {})", attempts);
                    }

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("QR storage interrupted");
                    break;
                } catch (Exception e) {
                    log.warn("❌ Error storing QR code (attempt {}): {}", attempts, e.getMessage());

                    if (attempts >= maxRetries) {
                        log.error("❌ Failed to store QR code after {} attempts", maxRetries, e);
                    }
                }
            }

            if (!success) {
                log.error("❌ Failed to store QR code after all retry attempts");
            }
        });
    }

    /**
     * Handle successful login
     */
    private void handleLoginSuccess() {
        log.info("✅ WhatsApp logged in successfully with session: {}", currentSessionId);
        qrHandlerActive.set(false); // Reset QR handler state

        // Notify the service about successful login
        CompletableFuture.runAsync(() -> {
            try {
                var whatsAppService = applicationContext.getBean(
                        "whatsAppService",
                        com.orbvpn.api.service.notification.WhatsAppService.class);

                if (whatsAppService != null) {
                    // The service will handle login success through its own listener
                    log.info("✅ Login success will be handled by WhatsAppService listener");
                } else {
                    log.warn("WhatsAppService not found during login success notification");
                }
            } catch (Exception e) {
                log.warn("Error notifying service of login success: {}", e.getMessage());
            }
        });
    }

    /**
     * Handle disconnection events - using method reference for better compatibility
     */
    private void handleDisconnect(Object reason) {
        log.info("❌ WhatsApp disconnected (session: {}): {}", currentSessionId, reason);
        qrHandlerActive.set(false); // Reset QR handler state

        // The service will handle disconnection through its own listener
        if (autoReconnect) {
            // Check if it's not a logout (string comparison for compatibility)
            String reasonStr = reason.toString().toLowerCase();
            if (!reasonStr.contains("logged_out") && !reasonStr.contains("logout")) {
                log.info("Auto-reconnect enabled, service will handle reconnection");
            }
        }
    }

    /**
     * Method to manually trigger connection - for service use
     * Returns CompletableFuture<Void> for consistency
     */
    public CompletableFuture<Void> connectWhatsApp() {
        if (whatsappInstance == null) {
            log.error("WhatsApp instance not initialized");
            return CompletableFuture.failedFuture(new IllegalStateException("WhatsApp instance not initialized"));
        }

        log.info("🔄 Manually triggering WhatsApp connection...");

        try {
            // Create a proper CompletableFuture<Void> with correct exception handling
            CompletableFuture<Void> result = new CompletableFuture<>();

            whatsappInstance.connect()
                    .thenAccept(whatsapp -> {
                        log.info("✅ WhatsApp connection completed successfully");
                        result.complete(null);
                    })
                    .exceptionally(throwable -> {
                        log.error("❌ WhatsApp connection failed: {}", throwable.getMessage());
                        result.completeExceptionally(new RuntimeException("Connection failed", throwable));
                        return null;
                    });

            return result;
        } catch (Exception e) {
            log.error("Error connecting WhatsApp: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Method to disconnect WhatsApp
     */
    public CompletableFuture<Void> disconnectWhatsApp() {
        if (whatsappInstance == null) {
            log.warn("WhatsApp instance not initialized");
            return CompletableFuture.completedFuture(null);
        }

        log.info("🔄 Manually disconnecting WhatsApp...");

        try {
            // Create a proper CompletableFuture<Void> with correct exception handling
            CompletableFuture<Void> result = new CompletableFuture<>();

            whatsappInstance.disconnect()
                    .thenAccept(disconnectResult -> {
                        log.info("✅ WhatsApp disconnection completed successfully");
                        result.complete(null);
                    })
                    .exceptionally(throwable -> {
                        log.error("❌ WhatsApp disconnection failed: {}", throwable.getMessage());
                        result.completeExceptionally(new RuntimeException("Disconnection failed", throwable));
                        return null;
                    });

            return result;
        } catch (Exception e) {
            log.error("Error disconnecting WhatsApp: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Method to force a fresh connection (disconnect + reconnect)
     */
    public CompletableFuture<Void> recreateConnection() {
        log.info("🔄 Recreating WhatsApp connection...");

        if (whatsappInstance == null) {
            log.error("WhatsApp instance not initialized");
            return CompletableFuture.failedFuture(new IllegalStateException("WhatsApp instance not initialized"));
        }

        // Create the final result future
        CompletableFuture<Void> result = new CompletableFuture<>();

        // First disconnect, then reconnect
        disconnectWhatsApp()
                .thenCompose(v -> {
                    log.info("Disconnect completed, waiting before reconnection...");
                    return CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(3000); // Wait 3 seconds
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    });
                })
                .thenCompose(v -> {
                    log.info("Starting fresh connection...");
                    return connectWhatsApp();
                })
                .thenAccept(v -> {
                    log.info("✅ Connection recreation completed successfully");
                    result.complete(null);
                })
                .exceptionally(throwable -> {
                    log.error("❌ Connection recreation failed: {}", throwable.getMessage());
                    result.completeExceptionally(new RuntimeException("Connection recreation failed", throwable));
                    return null;
                });

        return result;
    }

    /**
     * Enhanced session cleanup with better error handling
     */
    private void cleanSessionFiles() {
        try {
            log.info("🧹 Cleaning up WhatsApp session files...");

            // Common session file locations
            String[] sessionPaths = {
                    ".whatsapp_keys",
                    ".whatsapp_store",
                    "whatsapp_store",
                    "whatsapp_keys",
                    System.getProperty("user.home") + "/.whatsapp_keys",
                    System.getProperty("user.home") + "/.whatsapp_store",
                    System.getProperty("user.dir") + "/.whatsapp_keys",
                    System.getProperty("user.dir") + "/.whatsapp_store"
            };

            int deletedDirs = 0;
            int deletedFiles = 0;

            for (String path : sessionPaths) {
                File sessionDir = new File(path);
                if (sessionDir.exists()) {
                    log.info("Found session directory: {}", path);
                    if (deleteDirectory(sessionDir)) {
                        deletedDirs++;
                    }
                }
            }

            // Clean files in current directory
            File currentDir = new File(".");
            File[] files = currentDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName().toLowerCase();
                    if (name.startsWith("whatsapp") ||
                            name.contains("session") ||
                            name.endsWith(".db") ||
                            name.contains("cobalt_")) {

                        log.info("Deleting session file: {}", file.getName());
                        if (file.isDirectory()) {
                            if (deleteDirectory(file)) {
                                deletedDirs++;
                            }
                        } else {
                            if (file.delete()) {
                                deletedFiles++;
                            } else {
                                log.warn("Failed to delete file: {}", file.getName());
                            }
                        }
                    }
                }
            }

            log.info("✅ Session cleanup completed - Deleted {} directories and {} files",
                    deletedDirs, deletedFiles);

        } catch (Exception e) {
            log.warn("Error during session cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Enhanced directory deletion with better error handling
     */
    private boolean deleteDirectory(File directory) {
        try {
            if (!directory.exists()) {
                return false;
            }

            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        if (!file.delete()) {
                            log.warn("Failed to delete file: {}", file.getAbsolutePath());
                        }
                    }
                }
            }

            boolean deleted = directory.delete();
            if (deleted) {
                log.debug("Deleted directory: {}", directory.getName());
            } else {
                log.warn("Failed to delete directory: {}", directory.getName());
            }
            return deleted;

        } catch (Exception e) {
            log.warn("Error deleting directory {}: {}", directory.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Get current session information
     */
    public UUID getCurrentSessionId() {
        return currentSessionId;
    }

    /**
     * Check if QR handler is currently active
     */
    public boolean isQrHandlerActive() {
        return qrHandlerActive.get();
    }

    /**
     * Get WhatsApp instance for service use
     */
    public Whatsapp getWhatsappInstance() {
        return whatsappInstance;
    }

    /**
     * Check if WhatsApp instance is available and connected
     */
    public boolean isInstanceConnected() {
        return whatsappInstance != null && whatsappInstance.isConnected();
    }

    /**
     * Check if WhatsApp instance is available and authenticated
     * FIXED VERSION - Properly checks Optional JID
     */
    public boolean isInstanceAuthenticated() {
        if (whatsappInstance == null) {
            log.debug("WhatsApp instance is null");
            return false;
        }

        if (!whatsappInstance.isConnected()) {
            log.debug("WhatsApp instance is not connected");
            return false;
        }

        try {
            // Check if we have a valid store
            if (whatsappInstance.store() == null) {
                log.debug("WhatsApp store is null");
                return false;
            }

            // CRITICAL FIX: Check if JID exists AND has a value
            var jidOptional = whatsappInstance.store().jid();
            if (jidOptional == null || jidOptional.isEmpty()) {
                log.debug("WhatsApp JID is null or empty - not authenticated");
                return false;
            }

            // Get the actual JID value
            var actualJid = jidOptional.get();
            if (actualJid == null) {
                log.debug("WhatsApp JID value is null");
                return false;
            }

            // Additional check - try to access basic store data
            try {
                var contacts = whatsappInstance.store().contacts();
                log.debug("WhatsApp authenticated - JID: {}, Contacts: {}", actualJid, contacts.size());
                return true;
            } catch (Exception storeError) {
                log.debug("Error accessing WhatsApp store data: {}", storeError.getMessage());
                return false;
            }

        } catch (Exception e) {
            log.debug("Error checking WhatsApp authentication: {}", e.getMessage());
            return false;
        }
    }
}