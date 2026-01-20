package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.QrLoginSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public interface QrLoginSessionRepository extends JpaRepository<QrLoginSession, Long> {
    Optional<QrLoginSession> findBySessionId(String sessionId);

    Optional<QrLoginSession> findByQrCode(String qrCode);

    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}