package com.orbvpn.api.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
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
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrCode, BarcodeFormat.QR_CODE, 200, 200);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            log.error("Error generating QR code", e);
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
}