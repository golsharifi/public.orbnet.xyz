package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.BridgeConnectionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BridgeConnectionLogRepository extends JpaRepository<BridgeConnectionLog, Long> {

    Page<BridgeConnectionLog> findByUserIdOrderByConnectedAtDesc(Long userId, Pageable pageable);

    List<BridgeConnectionLog> findByUserIdAndStatusOrderByConnectedAtDesc(Long userId, String status);

    Optional<BridgeConnectionLog> findFirstByUserIdAndStatusOrderByConnectedAtDesc(Long userId, String status);

    @Query("SELECT b FROM BridgeConnectionLog b WHERE b.user.id = :userId " +
           "AND b.connectedAt >= :startDate AND b.connectedAt <= :endDate " +
           "ORDER BY b.connectedAt DESC")
    Page<BridgeConnectionLog> findByUserIdAndDateRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    @Query("SELECT COUNT(b) FROM BridgeConnectionLog b WHERE b.bridgeServerId = :bridgeServerId AND b.status = 'connected'")
    long countActiveConnectionsByBridgeServer(@Param("bridgeServerId") Long bridgeServerId);

    @Query("SELECT SUM(b.bytesSent) FROM BridgeConnectionLog b WHERE b.user.id = :userId")
    Long sumBytesSentByUserId(@Param("userId") Long userId);

    @Query("SELECT SUM(b.bytesReceived) FROM BridgeConnectionLog b WHERE b.user.id = :userId")
    Long sumBytesReceivedByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(b) FROM BridgeConnectionLog b WHERE b.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
}
