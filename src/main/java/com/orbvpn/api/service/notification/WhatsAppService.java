package com.orbvpn.api.service.notification;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.orbvpn.api.exception.ChatException;
import com.orbvpn.api.config.messaging.CobaltConfig;
import com.orbvpn.api.config.scheduler.SchedulerManager;
import com.orbvpn.api.domain.entity.WhatsAppMessage;
import com.orbvpn.api.event.MessageQueueEvent;
import com.orbvpn.api.event.WhatsAppQRCodeEvent;
import com.orbvpn.api.repository.WhatsAppMessageRepository;

import it.auties.whatsapp.api.Whatsapp;
import it.auties.whatsapp.api.DisconnectReason;
import it.auties.whatsapp.model.jid.Jid;
import it.auties.whatsapp.model.chat.Chat;
import it.auties.whatsapp.model.contact.Contact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledFuture;

@Service
@Slf4j
@RequiredArgsConstructor
@org.springframework.context.annotation.Lazy
public class WhatsAppService implements SmartLifecycle {
    private final Whatsapp whatsapp;
    private final SchedulerManager schedulerManager;
    private final MessageRateLimiter rateLimiter;
    private final WhatsAppMessageRepository messageRepository;

    @Autowired
    private CobaltConfig cobaltConfig;

    // Connection State Enum
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        WAITING_FOR_QR_SCAN,
        AUTHENTICATED,
        RECONNECTING,
        QR_GENERATION_REQUESTED
    }

    private volatile String currentQrCode;
    private volatile String qrCodeSvg;
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private boolean running = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long INITIAL_DELAY = 15L;
    private static final long CHECK_INTERVAL = 30L;
    private static final long QR_GENERATION_TIMEOUT = 30L; // 30 seconds timeout for QR generation
    private final Lock connectionLock = new ReentrantLock();

    // Exponential backoff constants
    private static final long BASE_RECONNECT_DELAY_SECONDS = 5L;
    private static final long MAX_RECONNECT_DELAY_SECONDS = 300L; // 5 minutes max
    private volatile long lastSuccessfulConnection = 0;

    private volatile long qrCodeTimestamp = 0;
    private static final long QR_CODE_EXPIRY_MS = 120000; // 2 minutes
    private static final long QR_REFRESH_INTERVAL = 90000; // Refresh every 90 seconds (before expiry)

    // New fields for better QR management
    private final AtomicBoolean qrGenerationInProgress = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> qrRefreshTask;
    private volatile ScheduledFuture<?> connectionMonitorTask;
    private volatile long lastQrGenerationAttempt = 0;
    private static final long MIN_QR_GENERATION_INTERVAL = 10000; // Min 10 seconds between attempts

    @PostConstruct
    public void init() {
        log.info("Initializing WhatsAppService for Cobalt 0.0.9");

        try {
            // Check authentication status on startup
            if (isAuthenticated()) {
                connectionState = ConnectionState.AUTHENTICATED;
                log.info("WhatsApp is authenticated on startup");
                clearQrCode();
            } else if (whatsapp.isConnected()) {
                connectionState = ConnectionState.WAITING_FOR_QR_SCAN;
                log.info("WhatsApp is connected but not authenticated - checking for QR code");

                // If connected but not authenticated, we should have a QR code or generate one
                if (currentQrCode == null || isQrCodeExpired()) {
                    scheduleQrGeneration("Startup - no valid QR code available");
                }
            } else {
                connectionState = ConnectionState.DISCONNECTED;
                log.info("WhatsApp not connected, will generate QR on first connect request");
            }

            // Add listeners to the existing Whatsapp instance
            whatsapp.addLoggedInListener(this::onLoginSuccess);
            whatsapp.addDisconnectedListener(this::onDisconnect);

            debugWhatsAppState();
        } catch (Exception e) {
            log.error("Error initializing WhatsApp service", e);
            connectionState = ConnectionState.DISCONNECTED;
        }
    }

    public void debugWhatsAppState() {
        try {
            log.info("=== WhatsApp Debug State ===");
            log.info("connectionState: {}", connectionState);
            log.info("whatsapp.isConnected(): {}", whatsapp.isConnected());
            log.info("isAuthenticated(): {}", isAuthenticated());
            log.info("currentQrCode: {}",
                    currentQrCode != null ? "Present (" + currentQrCode.length() + " chars)" : "null");
            log.info("qrCodeSvg: {}", qrCodeSvg != null ? "Present (" + qrCodeSvg.length() + " chars)" : "null");
            log.info("running: {}", running);
            log.info("reconnectAttempts: {}", reconnectAttempts);
            log.info("qrCodeAge: {}ms", getQrCodeAge());
            log.info("qrGenerationInProgress: {}", qrGenerationInProgress.get());
            log.info("lastQrGenerationAttempt: {}ms ago",
                    lastQrGenerationAttempt > 0 ? System.currentTimeMillis() - lastQrGenerationAttempt : -1);
            log.info("===========================");
        } catch (Exception e) {
            log.error("Error in debug state", e);
        }
    }

    /**
     * Check if WhatsApp is properly authenticated (not just connected)
     * FIXED VERSION - Properly checks for valid JID
     */
    public boolean isAuthenticated() {
        try {
            log.debug("🔍 Checking WhatsApp authentication status...");

            // First check if WhatsApp instance is connected
            if (!whatsapp.isConnected()) {
                log.debug("❌ WhatsApp is not connected");
                return false;
            }

            // Check if we have a valid store
            if (whatsapp.store() == null) {
                log.debug("❌ WhatsApp store is null");
                return false;
            }

            // CRITICAL FIX: Check if JID exists AND has a value
            var jidOptional = whatsapp.store().jid();
            if (jidOptional == null) {
                log.debug("❌ WhatsApp JID is null");
                return false;
            }

            // FIXED: Check if the Optional actually contains a value
            if (jidOptional.isEmpty()) {
                log.debug("❌ WhatsApp JID is empty - not authenticated");
                return false;
            }

            // Get the actual JID value
            var actualJid = jidOptional.get();
            if (actualJid == null) {
                log.debug("❌ WhatsApp JID value is null");
                return false;
            }

            // Additional validation - check if we can access contacts and they exist
            try {
                var contacts = whatsapp.store().contacts();
                // For a truly authenticated session, we should have some contacts or at least
                // be able to access them
                // An empty contact list is fine, but we should be able to access it without
                // errors
                log.debug("✅ WhatsApp is authenticated with JID: {} and {} contacts",
                        actualJid, contacts.size());
                return true;
            } catch (Exception contactError) {
                log.debug("❌ Cannot access contacts properly - not fully authenticated: {}", contactError.getMessage());
                return false;
            }

        } catch (Exception e) {
            log.debug("❌ Error checking authentication status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if WhatsApp is connected (for backward compatibility)
     */
    public boolean isConnected() {
        return isAuthenticated(); // Now returns authentication status instead of connection status
    }

    public void onLoginSuccess() {
        connectionState = ConnectionState.AUTHENTICATED;
        reconnectAttempts = 0;
        qrGenerationInProgress.set(false);
        lastSuccessfulConnection = System.currentTimeMillis();
        log.info("WhatsApp login successful - now authenticated");

        // Clear QR code after successful login
        schedulerManager.getScheduler().schedule(() -> {
            clearQrCode();
            cancelQrRefreshTask();
            log.info("Cleared QR code after successful authentication");
        }, 5L, TimeUnit.SECONDS);
    }

    /**
     * Calculate reconnection delay using exponential backoff.
     * @return delay in seconds
     */
    private long calculateReconnectDelay() {
        // Exponential backoff: baseDelay * 2^attempt, capped at maxDelay
        long delay = (long) (BASE_RECONNECT_DELAY_SECONDS * Math.pow(2, reconnectAttempts - 1));
        return Math.min(delay, MAX_RECONNECT_DELAY_SECONDS);
    }

    public void onDisconnect(DisconnectReason reason) {
        log.info("WhatsApp disconnected. Reason: {}", reason);

        // Update connection state based on reason
        if (reason == DisconnectReason.LOGGED_OUT) {
            connectionState = ConnectionState.DISCONNECTED;
            clearQrCode();
            cancelQrRefreshTask();
            qrGenerationInProgress.set(false);
        } else {
            connectionState = ConnectionState.WAITING_FOR_QR_SCAN;
            // Keep QR code available for reconnection if it's still valid
            if (isQrCodeExpired()) {
                log.info("QR code expired after disconnect, will generate new one");
                scheduleQrGeneration("Disconnect - QR expired");
            } else {
                log.info("Preserving valid QR code after disconnect for continued scanning");
                // Still schedule a refresh to ensure we don't get stuck
                scheduleQrRefresh();
            }
        }

        if (shouldAttemptReconnect(reason)) {
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++;
                connectionState = ConnectionState.RECONNECTING;
                long delay = calculateReconnectDelay();
                log.info("Attempting to reconnect in {}s... Attempt {}/{}", delay, reconnectAttempts, MAX_RECONNECT_ATTEMPTS);
                schedulerManager.getScheduler().schedule(this::connect, delay, TimeUnit.SECONDS);
            } else {
                log.error("Max reconnection attempts ({}) reached. Manual intervention required.", MAX_RECONNECT_ATTEMPTS);
                connectionState = ConnectionState.DISCONNECTED;
                // Try to generate QR code for manual reconnection
                scheduleQrGeneration("Max reconnect attempts reached");
            }
        }
    }

    private boolean shouldAttemptReconnect(DisconnectReason reason) {
        // Don't reconnect if user logged out intentionally
        return reason != DisconnectReason.LOGGED_OUT;
    }

    public synchronized void connect() {
        connectionLock.lock();
        try {
            log.info("Connect method called - Current state: {}", connectionState);
            debugWhatsAppState();

            if (isAuthenticated()) {
                connectionState = ConnectionState.AUTHENTICATED;
                log.info("WhatsApp is already authenticated");
                clearQrCode();
                cancelQrRefreshTask();
                return;
            }

            // Always trigger QR generation when connecting (regardless of current
            // connection state)
            // This ensures we get a fresh QR code for scanning
            log.info("🔄 Triggering QR code generation via connect()...");
            connectionState = ConnectionState.CONNECTING;

            try {
                // Use recreateConnection instead of connectWhatsApp to ensure QR generation
                log.info("Using CobaltConfig to recreate connection for QR generation...");
                cobaltConfig.recreateConnection()
                        .thenRun(() -> {
                            log.info("✅ Connection recreation completed successfully via connect()");
                            connectionState = ConnectionState.WAITING_FOR_QR_SCAN;
                        })
                        .exceptionally(throwable -> {
                            log.error("❌ Connection recreation failed via connect(): {}", throwable.getMessage());
                            connectionState = ConnectionState.DISCONNECTED;
                            return null;
                        });

            } catch (Exception e) {
                log.error("Error using CobaltConfig for connection: {}", e.getMessage(), e);
                connectionState = ConnectionState.DISCONNECTED;
            }

        } catch (Exception e) {
            log.error("Error during connect", e);
            connectionState = ConnectionState.DISCONNECTED;
            qrGenerationInProgress.set(false);
        } finally {
            connectionLock.unlock();
        }
    }

    @Override
    public void start() {
        log.info("Starting WhatsAppService lifecycle...");

        // Check initial authentication status
        if (isAuthenticated()) {
            connectionState = ConnectionState.AUTHENTICATED;
            clearQrCode();
        } else if (whatsapp.isConnected()) {
            connectionState = ConnectionState.WAITING_FOR_QR_SCAN;
            if (currentQrCode == null || isQrCodeExpired()) {
                scheduleQrGeneration("Service start - need QR code");
            }
        } else {
            connectionState = ConnectionState.DISCONNECTED;
        }

        // Start connection monitoring
        connectionMonitorTask = schedulerManager.getScheduler().scheduleAtFixedRate(
                this::checkConnection, INITIAL_DELAY, CHECK_INTERVAL, TimeUnit.SECONDS);
        running = true;

        log.info("WhatsAppService started. Connection State: {}", connectionState);
    }

    @Override
    public void stop() {
        log.info("Stopping WhatsAppService lifecycle...");
        disconnect();
        cancelQrRefreshTask();
        if (connectionMonitorTask != null) {
            connectionMonitorTask.cancel(false);
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void checkConnection() {
        connectionLock.lock();
        try {
            boolean authenticated = isAuthenticated();
            boolean connected = whatsapp.isConnected();

            // Update connection state based on actual status
            if (authenticated) {
                if (connectionState != ConnectionState.AUTHENTICATED) {
                    log.info("Connection check - Now authenticated");
                    connectionState = ConnectionState.AUTHENTICATED;
                    resetReconnectAttempts();
                    clearQrCode();
                    cancelQrRefreshTask();
                }
            } else if (connected) {
                // Connected but not authenticated
                if (connectionState == ConnectionState.AUTHENTICATED) {
                    log.info("Connection check - Lost authentication, need QR code");
                    connectionState = ConnectionState.WAITING_FOR_QR_SCAN;
                }

                // Check if QR code needs refresh or generation
                if (currentQrCode == null) {
                    log.info("Connection check - No QR code available, generating");
                    scheduleQrGeneration("Connection check - no QR");
                } else if (isQrCodeExpired()) {
                    log.info("Connection check - QR code expired, regenerating");
                    scheduleQrGeneration("Connection check - QR expired");
                } else if (getQrCodeAge() > QR_REFRESH_INTERVAL) {
                    log.info("Connection check - QR code needs refresh");
                    scheduleQrGeneration("Connection check - QR refresh");
                }
            } else {
                // Not connected at all
                if (connectionState != ConnectionState.DISCONNECTED &&
                        connectionState != ConnectionState.RECONNECTING &&
                        reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    log.info("Connection check - Lost connection, state: {}", connectionState);
                    connectionState = ConnectionState.RECONNECTING;
                    scheduleQrGeneration("Connection check - reconnecting");
                }
            }
        } catch (Exception e) {
            log.error("Error in connection check", e);
        } finally {
            connectionLock.unlock();
        }
    }

    private static final long DISCONNECT_TIMEOUT_SECONDS = 10;

    public synchronized void disconnect() {
        try {
            log.info("Disconnecting WhatsApp...");

            // Update state first
            connectionState = ConnectionState.DISCONNECTED;

            // Clear QR code and cancel tasks
            clearQrCode();
            cancelQrRefreshTask();
            qrGenerationInProgress.set(false);

            if (whatsapp != null) {
                try {
                    // Use CobaltConfig with timeout to prevent hanging
                    cobaltConfig.disconnectWhatsApp()
                            .orTimeout(DISCONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .thenRun(() -> log.info("WhatsApp disconnected successfully"))
                            .exceptionally(throwable -> {
                                if (throwable instanceof java.util.concurrent.TimeoutException) {
                                    log.warn("WhatsApp disconnect timed out after {}s", DISCONNECT_TIMEOUT_SECONDS);
                                } else {
                                    log.warn("Error during WhatsApp disconnect: {}", throwable.getMessage());
                                }
                                return null;
                            })
                            .join(); // Wait for completion with timeout
                } catch (Exception e) {
                    log.warn("Error during WhatsApp disconnect: {}", e.getMessage());
                }
            }

            // Reset reconnection attempts
            reconnectAttempts = 0;
            log.info("WhatsApp disconnect completed");

        } catch (Exception e) {
            log.error("Error during disconnect", e);
        }
    }

    /**
     * Schedule QR code generation with rate limiting and duplicate prevention
     */
    private void scheduleQrGeneration(String reason) {
        // Rate limiting check
        long now = System.currentTimeMillis();
        if (lastQrGenerationAttempt > 0 &&
                (now - lastQrGenerationAttempt) < MIN_QR_GENERATION_INTERVAL) {
            log.info("QR generation rate limited - last attempt {}ms ago",
                    now - lastQrGenerationAttempt);
            return;
        }

        // Check if generation is already in progress
        if (!qrGenerationInProgress.compareAndSet(false, true)) {
            log.info("QR generation already in progress, skipping request: {}", reason);
            return;
        }

        lastQrGenerationAttempt = now;
        log.info("Scheduling QR generation - Reason: {}", reason);

        schedulerManager.getScheduler().schedule(() -> {
            try {
                triggerQrCodeGeneration();
            } finally {
                // Reset flag after timeout
                schedulerManager.getScheduler().schedule(() -> {
                    if (qrGenerationInProgress.get()) {
                        log.warn("QR generation timeout reached, resetting flag");
                        qrGenerationInProgress.set(false);
                    }
                }, QR_GENERATION_TIMEOUT, TimeUnit.SECONDS);
            }
        }, 1L, TimeUnit.SECONDS);
    }

    /**
     * Schedule QR code refresh (less aggressive than full regeneration)
     */
    private void scheduleQrRefresh() {
        if (qrRefreshTask != null) {
            qrRefreshTask.cancel(false);
        }

        long refreshDelay = Math.max(5000, QR_REFRESH_INTERVAL - getQrCodeAge());
        qrRefreshTask = schedulerManager.getScheduler().schedule(() -> {
            if (!isAuthenticated() && (currentQrCode == null || isQrCodeExpired())) {
                log.info("QR refresh triggered");
                scheduleQrGeneration("Scheduled refresh");
            }
        }, refreshDelay, TimeUnit.MILLISECONDS);
    }

    private void cancelQrRefreshTask() {
        if (qrRefreshTask != null) {
            qrRefreshTask.cancel(false);
            qrRefreshTask = null;
        }
    }

    // Replace the updateQrCode method in WhatsAppService.java with this:

    public synchronized void updateQrCode(String qrCode) {
        if (qrCode == null || qrCode.trim().isEmpty()) {
            log.warn("Received null or empty QR code");
            qrGenerationInProgress.set(false);
            return;
        }

        try {
            log.info("✅ Updating QR code - Raw QR length: {}", qrCode.length());

            // Store the raw QR code directly (don't double-encode)
            currentQrCode = qrCode;

            // Generate SVG version for display
            qrCodeSvg = generateQRCodeSvg(qrCode);

            // Also create a base64 encoded version for the HTML interface
            String base64QrCode = Base64.getEncoder().encodeToString(qrCode.getBytes());

            // Record timestamp
            qrCodeTimestamp = System.currentTimeMillis();

            // Update connection state
            if (connectionState != ConnectionState.AUTHENTICATED) {
                connectionState = ConnectionState.WAITING_FOR_QR_SCAN;
            }

            // Clear generation flag
            qrGenerationInProgress.set(false);

            // Schedule next refresh
            scheduleQrRefresh();

            log.info("🎯 QR code updated successfully!");
            log.info("   - Raw QR length: {}", currentQrCode.length());
            log.info("   - Base64 length: {}", base64QrCode.length());
            log.info("   - SVG available: {}", qrCodeSvg != null);
            log.info("   - Timestamp: {}", qrCodeTimestamp);
            log.info("   - Connection State: {}", connectionState);
            log.info("   - QR Preview: {}", qrCode.length() > 50 ? qrCode.substring(0, 50) + "..." : qrCode);

        } catch (Exception e) {
            log.error("❌ Failed to update QR code", e);
            qrGenerationInProgress.set(false);
        }
    }

    public boolean isQrCodeExpired() {
        if (currentQrCode == null || qrCodeTimestamp == 0) {
            return true;
        }

        long age = System.currentTimeMillis() - qrCodeTimestamp;
        boolean expired = age > QR_CODE_EXPIRY_MS;

        if (expired) {
            log.debug("QR code expired - Age: {}ms, Max: {}ms", age, QR_CODE_EXPIRY_MS);
        }

        return expired;
    }

    private void clearQrCode() {
        currentQrCode = null;
        qrCodeSvg = null;
        qrCodeTimestamp = 0;
    }

    public void clearExpiredQrCode() {
        if (isQrCodeExpired()) {
            log.info("Clearing expired QR code");
            clearQrCode();
        }
    }

    public String getQrCode() {
        clearExpiredQrCode(); // Clear if expired
        return currentQrCode;
    }

    public String getQrCodeSvg() {
        clearExpiredQrCode(); // Clear if expired
        return qrCodeSvg;
    }

    public synchronized void triggerQrCodeGeneration() {
        try {
            log.info("🔄 Triggering new QR code generation...");

            // Clear existing QR code first
            clearQrCode();
            connectionState = ConnectionState.QR_GENERATION_REQUESTED;

            // Use CobaltConfig to recreate the connection
            try {
                log.info("Using CobaltConfig to recreate connection for QR generation...");
                cobaltConfig.recreateConnection()
                        .thenRun(() -> {
                            log.info("✅ Connection recreation completed for QR generation");
                            connectionState = ConnectionState.WAITING_FOR_QR_SCAN;
                            qrGenerationInProgress.set(false);
                        })
                        .exceptionally(throwable -> {
                            log.error("❌ Error in QR generation via connection recreation: {}", throwable.getMessage());
                            connectionState = ConnectionState.DISCONNECTED;
                            qrGenerationInProgress.set(false);
                            return null;
                        });

            } catch (Exception e) {
                log.error("Error using CobaltConfig for QR generation: {}", e.getMessage(), e);
                connectionState = ConnectionState.DISCONNECTED;
                qrGenerationInProgress.set(false);
            }

        } catch (Exception e) {
            log.error("Error triggering QR code generation", e);
            connectionState = ConnectionState.DISCONNECTED;
            qrGenerationInProgress.set(false);
        }
    }

    // 2. Replace the forceRecreateInstance() method:
    public synchronized void forceRecreateInstance() {
        try {
            log.info("🔄 Force recreating WhatsApp instance...");

            // Clear internal state
            clearQrCode();
            cancelQrRefreshTask();
            connectionState = ConnectionState.CONNECTING;
            reconnectAttempts = 0;
            qrGenerationInProgress.set(false);

            // Use CobaltConfig to handle the recreation
            try {
                cobaltConfig.recreateConnection()
                        .thenRun(() -> {
                            log.info("✅ Force recreation completed successfully");
                            connectionState = ConnectionState.WAITING_FOR_QR_SCAN;
                        })
                        .exceptionally(throwable -> {
                            log.error("❌ Error in force recreation: {}", throwable.getMessage());
                            connectionState = ConnectionState.DISCONNECTED;
                            qrGenerationInProgress.set(false);
                            return null;
                        });

            } catch (Exception e) {
                log.error("Error in force recreate instance: {}", e.getMessage());
                connectionState = ConnectionState.DISCONNECTED;
                qrGenerationInProgress.set(false);
            }

        } catch (Exception e) {
            log.error("Error in force recreate instance: {}", e.getMessage());
            connectionState = ConnectionState.DISCONNECTED;
            qrGenerationInProgress.set(false);
        }
    }

    public void resetReconnectAttempts() {
        reconnectAttempts = 0;
        log.info("Reconnect attempts reset");
    }

    /**
     * Get health statistics for monitoring.
     */
    public String getHealthStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("WhatsApp Health Stats:\n");
        stats.append("  - Connection State: ").append(connectionState).append("\n");
        stats.append("  - Authenticated: ").append(isAuthenticated()).append("\n");
        stats.append("  - Running: ").append(running).append("\n");
        stats.append("  - Reconnect Attempts: ").append(reconnectAttempts).append("/").append(MAX_RECONNECT_ATTEMPTS).append("\n");

        if (lastSuccessfulConnection > 0) {
            long uptime = System.currentTimeMillis() - lastSuccessfulConnection;
            stats.append("  - Last Connection: ").append(uptime / 1000).append("s ago\n");
        } else {
            stats.append("  - Last Connection: Never\n");
        }

        if (currentQrCode != null) {
            stats.append("  - QR Code: Available (").append(getQrCodeAge() / 1000).append("s old)\n");
        } else {
            stats.append("  - QR Code: Not available\n");
        }

        stats.append("  - QR Generation In Progress: ").append(qrGenerationInProgress.get()).append("\n");

        return stats.toString();
    }

    /**
     * Check if the service is healthy.
     */
    public boolean isHealthy() {
        // Healthy if authenticated or has valid QR code for authentication
        if (isAuthenticated()) {
            return true;
        }

        // If not authenticated, consider healthy if we have QR code available for scanning
        if (currentQrCode != null && !isQrCodeExpired()) {
            return true;
        }

        // If we're in the process of generating QR, that's also acceptable
        if (qrGenerationInProgress.get() || connectionState == ConnectionState.CONNECTING ||
                connectionState == ConnectionState.QR_GENERATION_REQUESTED) {
            return true;
        }

        // Otherwise, check if we've exceeded max retries
        return reconnectAttempts < MAX_RECONNECT_ATTEMPTS;
    }

    public String getConnectionStatus() {
        try {
            boolean authenticated = isAuthenticated();
            boolean connected = whatsapp.isConnected();
            return String.format(
                    "State: %s, Connected: %s, Authenticated: %s, Running: %s, Reconnect Attempts: %d/%d, QR Generation: %s",
                    connectionState, connected, authenticated, running, reconnectAttempts, MAX_RECONNECT_ATTEMPTS,
                    qrGenerationInProgress.get() ? "In Progress" : "Idle");
        } catch (Exception e) {
            return "Error getting status: " + e.getMessage();
        }
    }

    public int getQrCodeAge() {
        if (qrCodeTimestamp == 0) {
            return -1;
        }
        long age = System.currentTimeMillis() - qrCodeTimestamp;
        return (int) age;
    }

    // Get connection state for debugging
    public ConnectionState getConnectionState() {
        return connectionState;
    }

    /**
     * Send a WhatsApp message with delivery tracking.
     *
     * @param phoneNumber The recipient's phone number
     * @param message The message content
     * @return CompletableFuture that completes when the message is sent
     */
    public CompletableFuture<Void> sendMessage(String phoneNumber, String message) {
        // Create tracking record
        WhatsAppMessage trackingRecord = WhatsAppMessage.builder()
                .phoneNumber(phoneNumber)
                .message(message.length() > 1000 ? message.substring(0, 1000) + "..." : message)
                .build();

        try {
            trackingRecord = messageRepository.save(trackingRecord);
        } catch (Exception e) {
            log.warn("Failed to save message tracking record: {}", e.getMessage());
            // Continue even if tracking fails
        }

        final WhatsAppMessage finalRecord = trackingRecord;
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (!isAuthenticated()) {
            updateMessageStatus(finalRecord, null, "WhatsApp not authenticated");
            future.completeExceptionally(new ChatException("WhatsApp not authenticated"));
            return future;
        }

        // Check rate limit first
        if (!rateLimiter.tryConsumeWhatsApp(phoneNumber)) {
            if (finalRecord != null && finalRecord.getId() != null) {
                finalRecord.markAsRateLimited();
                try {
                    messageRepository.save(finalRecord);
                } catch (Exception e) {
                    log.warn("Failed to update rate-limited message status: {}", e.getMessage());
                }
            }
            future.completeExceptionally(new ChatException("Rate limit exceeded for phone number: " + maskPhoneForLog(phoneNumber)));
            return future;
        }

        String formattedNumber;
        try {
            formattedNumber = formatPhoneNumber(phoneNumber);
        } catch (IllegalArgumentException e) {
            updateMessageStatus(finalRecord, null, e.getMessage());
            future.completeExceptionally(new ChatException("Invalid phone number: " + e.getMessage()));
            return future;
        }

        // Mark as sending
        if (finalRecord != null && finalRecord.getId() != null) {
            finalRecord.markAsSending();
            try {
                messageRepository.save(finalRecord);
            } catch (Exception e) {
                log.warn("Failed to update sending status: {}", e.getMessage());
            }
        }

        try {
            Optional<Chat> existingChat = findExistingChat(formattedNumber);

            if (existingChat.isPresent()) {
                whatsapp.sendMessage(existingChat.get(), message)
                        .orTimeout(30, TimeUnit.SECONDS)
                        .thenAccept(messageInfo -> {
                            String msgId = messageInfo != null ? messageInfo.id().toString() : null;
                            log.info("Message sent to existing chat {}", maskPhoneForLog(phoneNumber));
                            updateMessageStatus(finalRecord, msgId, null);
                            future.complete(null);
                        })
                        .exceptionally(throwable -> {
                            log.error("Failed to send message to existing chat {}", maskPhoneForLog(phoneNumber), throwable);
                            updateMessageStatus(finalRecord, null, throwable.getMessage());
                            future.completeExceptionally(
                                    new ChatException("Failed to send message: " + throwable.getMessage()));
                            return null;
                        });
            } else {
                Jid contactJid = Jid.of(formattedNumber + "@s.whatsapp.net");
                whatsapp.sendMessage(contactJid, message)
                        .orTimeout(30, TimeUnit.SECONDS)
                        .thenAccept(messageInfo -> {
                            String msgId = messageInfo != null ? messageInfo.id().toString() : null;
                            log.info("Message sent to new contact {}", maskPhoneForLog(phoneNumber));
                            updateMessageStatus(finalRecord, msgId, null);
                            future.complete(null);
                        })
                        .exceptionally(throwable -> {
                            log.error("Failed to send message to new contact {}", maskPhoneForLog(phoneNumber), throwable);
                            updateMessageStatus(finalRecord, null, throwable.getMessage());
                            future.completeExceptionally(
                                    new ChatException("Failed to send message: " + throwable.getMessage()));
                            return null;
                        });
            }

            return future;
        } catch (Exception e) {
            log.error("Error creating message for {}: {}", maskPhoneForLog(phoneNumber), e.getMessage());
            updateMessageStatus(finalRecord, null, e.getMessage());
            future.completeExceptionally(new ChatException("Failed to create message: " + e.getMessage()));
            return future;
        }
    }

    /**
     * Update message tracking record status.
     */
    private void updateMessageStatus(WhatsAppMessage record, String messageId, String error) {
        if (record == null || record.getId() == null) {
            return;
        }

        try {
            if (error != null) {
                record.markAsFailed(error.length() > 500 ? error.substring(0, 500) : error);
            } else {
                record.markAsSent(messageId);
            }
            messageRepository.save(record);
        } catch (Exception e) {
            log.warn("Failed to update message status: {}", e.getMessage());
        }
    }

    private Optional<Chat> findExistingChat(String formattedNumber) {
        try {
            String whatsappJid = formattedNumber + "@s.whatsapp.net";

            Optional<Chat> chatByJid = whatsapp.store().findChatByJid(Jid.of(whatsappJid));
            if (chatByJid.isPresent()) {
                return chatByJid;
            }

            Optional<Contact> contact = whatsapp.store().findContactByJid(Jid.of(whatsappJid));
            if (contact.isPresent()) {
                return whatsapp.store().findChatByJid(contact.get().jid());
            }

            Optional<Contact> contactByName = whatsapp.store().findContactByName(formattedNumber);
            if (contactByName.isPresent()) {
                return whatsapp.store().findChatByJid(contactByName.get().jid());
            }

            return Optional.empty();
        } catch (Exception e) {
            log.debug("Error finding existing chat for {}: {}", formattedNumber, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Format phone number for WhatsApp JID.
     * Supports international numbers with various formats.
     *
     * @param phoneNumber Phone number in various formats (with or without +, country code, etc.)
     * @return Formatted phone number without + prefix, ready for WhatsApp JID
     */
    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty");
        }

        // Remove all non-digit characters except + at the start
        String cleaned = phoneNumber.trim();
        boolean hasPlus = cleaned.startsWith("+");
        cleaned = cleaned.replaceAll("[^\\d]", "");

        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Phone number must contain digits");
        }

        // If it started with + or is long enough, assume it has country code
        if (hasPlus || cleaned.length() > 10) {
            // Already has country code, return as-is
            return cleaned;
        }

        // Handle common formats without country code
        // Note: This is a best-effort guess; users should provide full international numbers

        // 10-digit number could be US/Canada (1) or other countries
        // 11-digit starting with 0 might be UK/others with leading 0
        if (cleaned.length() == 10) {
            // Assume US/Canada if 10 digits - this is common for US apps
            // Log a warning that this is an assumption
            log.debug("Assuming US/Canada country code for 10-digit number: {}",
                    maskPhoneForLog(cleaned));
            return "1" + cleaned;
        }

        // If it starts with 0, it might be a local number with leading 0
        // Some countries use leading 0 for local numbers (UK, Germany, etc.)
        if (cleaned.startsWith("0") && cleaned.length() >= 10) {
            log.debug("Phone number starts with 0, may need country code: {}",
                    maskPhoneForLog(cleaned));
            // Remove leading 0 but don't add country code - user should provide it
            return cleaned.substring(1);
        }

        // For other lengths, return as-is and let WhatsApp handle validation
        log.debug("Using phone number as provided: {}", maskPhoneForLog(cleaned));
        return cleaned;
    }

    /**
     * Mask phone number for logging (privacy protection)
     */
    private String maskPhoneForLog(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return phone.substring(0, 2) + "****" + phone.substring(phone.length() - 2);
    }

    public boolean testConnection(String phoneNumber) {
        if (!isAuthenticated()) {
            log.warn("WhatsApp service is not authenticated");
            return false;
        }

        try {
            String formattedNumber = formatPhoneNumber(phoneNumber);
            Optional<Chat> existingChat = findExistingChat(formattedNumber);

            CompletableFuture<Boolean> testFuture;

            if (existingChat.isPresent()) {
                testFuture = whatsapp.sendMessage(existingChat.get(), "Connection test")
                        .thenApply(result -> true)
                        .exceptionally(throwable -> {
                            log.error("Connection test failed for existing chat: {}", throwable.getMessage());
                            return false;
                        });
            } else {
                Jid contactJid = Jid.of(formattedNumber + "@s.whatsapp.net");
                testFuture = whatsapp.sendMessage(contactJid, "Connection test")
                        .thenApply(result -> true)
                        .exceptionally(throwable -> {
                            log.error("Connection test failed for new contact: {}", throwable.getMessage());
                            return false;
                        });
            }

            return testFuture.get(35, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to test WhatsApp connection for number: {}", phoneNumber, e);
            return false;
        }
    }

    @EventListener
    public void handleMessageQueueEvent(MessageQueueEvent event) {
        if (event.getType() == MessageQueueEvent.MessageType.WHATSAPP) {
            try {
                sendMessage(event.getRecipient(), event.getMessage());
            } catch (Exception e) {
                log.error("Error handling message queue event for WhatsApp: {}", e.getMessage(), e);
            }
        }
    }

    @EventListener
    public void handleQRCodeEvent(WhatsAppQRCodeEvent event) {
        try {
            log.info("🎯 QR code event received in WhatsAppService!");
            log.info("QR code from event length: {}", event.getQrCode().length());
            updateQrCode(event.getQrCode());
            log.info("✅ QR code updated successfully from event");
            debugWhatsAppState();
        } catch (Exception e) {
            log.error("❌ Error handling QR code event", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up WhatsAppService...");
        try {
            cancelQrRefreshTask();
            if (connectionMonitorTask != null) {
                connectionMonitorTask.cancel(false);
            }

            if (schedulerManager != null) {
                schedulerManager.shutdownScheduler();
            }

            if (whatsapp != null && whatsapp.isConnected()) {
                try {
                    // Use timeout to prevent hanging during shutdown
                    whatsapp.disconnect()
                            .orTimeout(5, TimeUnit.SECONDS)
                            .exceptionally(throwable -> {
                                log.warn("Disconnect during cleanup timed out or failed: {}", throwable.getMessage());
                                return null;
                            })
                            .join();
                    connectionState = ConnectionState.DISCONNECTED;
                    log.info("WhatsApp disconnected during cleanup");
                } catch (Exception e) {
                    log.warn("Error disconnecting during cleanup: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error during cleanup", e);
        }
    }

    private String generateQRCodeSvg(String content) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 256, 256);
            int width = matrix.getWidth();
            int height = matrix.getHeight();

            StringBuilder svgBuilder = new StringBuilder();
            svgBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            svgBuilder.append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" width=\"").append(width)
                    .append("\" height=\"").append(height).append("\" viewBox=\"0 0 ").append(width).append(" ")
                    .append(height).append("\">\n");
            svgBuilder.append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n");

            for (int y = 0; y < height; y++) {
                int xStart = -1;
                for (int x = 0; x < width; x++) {
                    if (matrix.get(x, y)) {
                        if (xStart == -1) {
                            xStart = x;
                        }
                    } else {
                        if (xStart != -1) {
                            svgBuilder.append("<rect x=\"").append(xStart).append("\" y=\"").append(y)
                                    .append("\" width=\"").append(x - xStart)
                                    .append("\" height=\"1\" fill=\"black\"/>\n");
                            xStart = -1;
                        }
                    }
                }
                if (xStart != -1) {
                    svgBuilder.append("<rect x=\"").append(xStart).append("\" y=\"").append(y)
                            .append("\" width=\"").append(width - xStart).append("\" height=\"1\" fill=\"black\"/>\n");
                }
            }

            svgBuilder.append("</svg>");
            return svgBuilder.toString();
        } catch (Exception e) {
            log.error("Failed to generate QR code SVG", e);
            return null;
        }
    }

    // Additional utility methods
    public java.util.Collection<Chat> getAllChats() {
        try {
            return whatsapp.store().chats();
        } catch (Exception e) {
            log.error("Error getting all chats", e);
            return java.util.Collections.emptyList();
        }
    }

    public java.util.Collection<Contact> getAllContacts() {
        try {
            return whatsapp.store().contacts();
        } catch (Exception e) {
            log.error("Error getting all contacts", e);
            return java.util.Collections.emptyList();
        }
    }

    public Optional<Chat> findChatByName(String name) {
        try {
            return whatsapp.store().findChatByName(name);
        } catch (Exception e) {
            log.error("Error finding chat by name: {}", name, e);
            return Optional.empty();
        }
    }

    /**
     * Enhanced debug method to check authentication details
     */
    public void debugAuthenticationState() {
        try {
            log.info("=== AUTHENTICATION DEBUG ===");
            log.info("whatsapp.isConnected(): {}", whatsapp.isConnected());
            log.info("whatsapp.store() != null: {}", whatsapp.store() != null);

            if (whatsapp.store() != null) {
                log.info("whatsapp.store().jid() != null: {}", whatsapp.store().jid() != null);
                if (whatsapp.store().jid() != null) {
                    log.info("whatsapp.store().jid(): {}", whatsapp.store().jid());
                }

                try {
                    var contacts = whatsapp.store().contacts();
                    log.info("contacts.size(): {}", contacts.size());
                } catch (Exception e) {
                    log.info("Error accessing contacts: {}", e.getMessage());
                }

                try {
                    var chats = whatsapp.store().chats();
                    log.info("chats.size(): {}", chats.size());
                } catch (Exception e) {
                    log.info("Error accessing chats: {}", e.getMessage());
                }
            }

            log.info("connectionState: {}", connectionState);
            log.info("isAuthenticated() result: {}", isAuthenticated());
            log.info("cobaltConfig.isInstanceAuthenticated(): {}",
                    cobaltConfig != null ? cobaltConfig.isInstanceAuthenticated() : "cobaltConfig is null");
            log.info("============================");
        } catch (Exception e) {
            log.error("Error in authentication debug", e);
        }
    }

    /**
     * Detailed debug method to understand why isAuthenticated() is returning true
     */
    public void debugAuthenticationDetails() {
        try {
            log.info("=== DETAILED AUTHENTICATION DEBUG ===");

            // Basic connection check
            boolean connected = whatsapp.isConnected();
            log.info("Step 1 - whatsapp.isConnected(): {}", connected);

            if (!connected) {
                log.info("RESULT: Not authenticated - not connected");
                log.info("=====================================");
                return;
            }

            // Store check
            boolean hasStore = whatsapp.store() != null;
            log.info("Step 2 - whatsapp.store() != null: {}", hasStore);

            if (!hasStore) {
                log.info("RESULT: Not authenticated - no store");
                log.info("=====================================");
                return;
            }

            // JID check
            boolean hasJid = whatsapp.store().jid() != null;
            log.info("Step 3 - whatsapp.store().jid() != null: {}", hasJid);

            if (hasJid) {
                log.info("Step 3a - JID value: {}", whatsapp.store().jid());
            } else {
                log.info("RESULT: Not authenticated - no JID");
                log.info("=====================================");
                return;
            }

            // Contacts access check
            try {
                var contacts = whatsapp.store().contacts();
                log.info("Step 4 - Contacts accessible: true, count: {}", contacts.size());

                // Additional checks
                try {
                    var chats = whatsapp.store().chats();
                    log.info("Step 5 - Chats accessible: true, count: {}", chats.size());
                } catch (Exception chatError) {
                    log.info("Step 5 - Chats accessible: false, error: {}", chatError.getMessage());
                }

                log.info("RESULT: AUTHENTICATED (all checks passed)");

            } catch (Exception contactError) {
                log.info("Step 4 - Contacts accessible: false, error: {}", contactError.getMessage());
                log.info("RESULT: Not authenticated - cannot access contacts");
            }

            // CobaltConfig check
            if (cobaltConfig != null) {
                boolean cobaltAuth = cobaltConfig.isInstanceAuthenticated();
                log.info("CobaltConfig.isInstanceAuthenticated(): {}", cobaltAuth);
            }

            log.info("=====================================");

        } catch (Exception e) {
            log.error("Error in detailed authentication debug", e);
        }
    }

}