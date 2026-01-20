package com.orbvpn.api.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.orbvpn.api.domain.dto.QrLoginResponse;
import com.orbvpn.api.domain.dto.AuthenticatedUser;
import com.orbvpn.api.domain.entity.QrLoginSession;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.QrLoginSessionRepository;
import com.orbvpn.api.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrLoginService {
    private final QrLoginSessionRepository qrLoginSessionRepository;
    private final UserService userService;

    private static final String APP_SCHEME = "orbvpn"; // Your app's URL scheme
    private static final String APP_HOST = "qrlogin"; // Deep link path

    @Transactional
    public QrLoginResponse generateQrCode() {
        return createNewSession(null);
    }

    @Transactional
    public QrLoginResponse refreshQrCode(String oldSessionId) {
        return createNewSession(oldSessionId);
    }

    private QrLoginResponse createNewSession(String oldSessionId) {
        // Invalidate old session if it exists
        if (oldSessionId != null) {
            QrLoginSession oldSession = qrLoginSessionRepository.findBySessionId(oldSessionId)
                    .orElse(null);
            if (oldSession != null) {
                oldSession.setUsed(true);
                qrLoginSessionRepository.save(oldSession);
                log.info("Invalidated old QR session: {}", oldSessionId);
            }
        }

        String sessionId = UUID.randomUUID().toString();
        String qrCodeValue = UUID.randomUUID().toString();

        // Create deep link URL
        String deepLinkUrl = String.format("%s://%s?code=%s",
                APP_SCHEME,
                APP_HOST,
                qrCodeValue);

        // Create QR code content that includes both code and deep link
        String qrCodeContent = String.format("{\"code\":\"%s\",\"url\":\"%s\"}",
                qrCodeValue,
                deepLinkUrl);

        QrLoginSession session = new QrLoginSession();
        session.setSessionId(sessionId);
        session.setQrCode(qrCodeValue);
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        qrLoginSessionRepository.save(session);
        log.info("Generated new QR login session with ID: {}", sessionId);

        // Generate QR code with the JSON content
        String qrCodeImage = generateQrCodeImage(qrCodeContent);

        return QrLoginResponse.builder()
                .qrCodeImage(qrCodeImage)
                .sessionId(sessionId)
                .qrCodeValue(qrCodeValue)
                .deepLinkUrl(deepLinkUrl)
                .expiresAt(session.getExpiresAt())
                .status("ACTIVE")
                .build();
    }

    private String generateQrCodeImage(String qrCode) {
        return generateQrCodeImage(qrCode, ErrorCorrectionLevel.Q);
    }

    /**
     * Generate QR code image with configurable error correction level.
     * Use LOW for very large content (JWT tokens), QUARTILE for normal content.
     */
    private String generateQrCodeImage(String qrCode, ErrorCorrectionLevel errorCorrectionLevel) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();

            // Configure QR code with specified error correction level
            // L (LOW): 7% recovery, maximum capacity - use for large content like JWT tokens
            // M (MEDIUM): 15% recovery
            // Q (QUARTILE): 25% recovery - good balance for moderate content
            // H (HIGH): 30% recovery, minimum capacity
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, errorCorrectionLevel);
            hints.put(EncodeHintType.MARGIN, 2); // Small margin for cleaner appearance
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            // Use larger size (500x500) to handle long content like JWT tokens
            BitMatrix bitMatrix = qrCodeWriter.encode(qrCode, BarcodeFormat.QR_CODE, 500, 500, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            log.error("Error generating QR code with {} error correction, content length: {}",
                    errorCorrectionLevel, qrCode.length(), e);
            throw new BadRequestException("Error generating QR code");
        }
    }

    @Transactional
    public void confirmQrLogin(String qrCode, User user) {
        if (qrCode == null || qrCode.trim().isEmpty()) {
            throw new BadRequestException("QR code cannot be empty");
        }

        if (!user.isActive() || !user.isEnabled()) {
            log.warn("Inactive or disabled user attempted QR login: {}", user.getId());
            throw new BadRequestException("User account is not active");
        }

        QrLoginSession session = qrLoginSessionRepository.findByQrCode(qrCode)
                .orElseThrow(() -> {
                    log.warn("Attempt to use invalid QR code: {}", qrCode);
                    return new BadRequestException("Invalid QR code");
                });

        if (session.isUsed()) {
            log.warn("Attempt to use already used QR code: {}", qrCode);
            throw new BadRequestException("QR code already used");
        }

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Attempt to use expired QR code: {}", qrCode);
            throw new BadRequestException("QR code has expired");
        }

        session.setUser(user);
        session.setConfirmed(true);
        qrLoginSessionRepository.save(session);
        log.info("QR login confirmed for user: {} with session: {}", user.getId(), session.getSessionId());
    }

    @Transactional
    public AuthenticatedUser checkQrLoginStatus(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new BadRequestException("Session ID cannot be empty");
        }

        QrLoginSession session = qrLoginSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> {
                    log.warn("Attempt to check invalid session: {}", sessionId);
                    return new BadRequestException("Invalid session");
                });

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Session expired: {}", sessionId);
            throw new BadRequestException("Session expired");
        }

        if (session.isConfirmed() && !session.isUsed()) {
            User user = session.getUser();

            if (!user.isActive() || !user.isEnabled()) {
                log.warn("User status changed during QR login process: {}", user.getId());
                throw new BadRequestException("User account is no longer active");
            }

            session.setUsed(true);
            qrLoginSessionRepository.save(session);

            log.info("Successfully logging in user via QR code: {}", user.getId());
            return userService.loginInfo(user);
        }

        return null;
    }

    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    @Transactional
    public void cleanupExpiredSessions() {
        qrLoginSessionRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.debug("Cleaned up expired QR login sessions");
    }

    /**
     * Generate a direct login QR code for an authenticated user.
     * This QR code contains the user's tokens and can be scanned by
     * another device to log in directly without polling.
     *
     * Use case: Logged-in web user shows QR in Settings,
     * mobile app scans to get logged in instantly.
     *
     * @param user The authenticated user
     * @return QR code response with embedded tokens
     */
    @Transactional
    public QrLoginResponse generateDirectLoginQr(User user) {
        if (!user.isActive() || !user.isEnabled()) {
            log.warn("Inactive or disabled user attempted to generate login QR: {}", user.getId());
            throw new BadRequestException("User account is not active");
        }

        // Generate tokens for the user
        AuthenticatedUser authUser = userService.loginInfo(user);

        String sessionId = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        // Create deep link URL with tokens (like magic link)
        String deepLinkUrl = String.format("%s://login?token=%s&refreshToken=%s",
                APP_SCHEME,
                authUser.getAccessToken(),
                authUser.getRefreshToken() != null ? authUser.getRefreshToken() : "");

        // Create simplified QR code content - just type and URL (tokens are in URL)
        // This reduces QR code size by not repeating tokens 3 times
        String qrCodeContent = String.format(
                "{\"type\":\"direct_login\",\"url\":\"%s\"}",
                deepLinkUrl);

        log.debug("Direct login QR content length: {} characters", qrCodeContent.length());

        // Generate QR code image with LOW error correction for maximum capacity
        // JWT tokens can be 1000+ characters each, requiring maximum QR capacity
        // LOW (L) provides 7% error recovery but can hold ~7000 alphanumeric characters
        String qrCodeImage = generateQrCodeImage(qrCodeContent, ErrorCorrectionLevel.L);

        log.info("Generated direct login QR for user: {}", user.getId());

        return QrLoginResponse.builder()
                .qrCodeImage(qrCodeImage)
                .sessionId(sessionId)
                .qrCodeValue(deepLinkUrl)  // Use deep link as the value
                .deepLinkUrl(deepLinkUrl)
                .expiresAt(expiresAt)
                .status("DIRECT_LOGIN")
                .build();
    }
}