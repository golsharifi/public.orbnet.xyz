package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.TrialHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrialHistoryRepository extends JpaRepository<TrialHistory, Long> {
    Optional<TrialHistory> findFirstByUserIdOrderByTrialStartDateDesc(int userId);

    Optional<TrialHistory> findByTransactionId(String transactionId);

    List<TrialHistory> findByUserId(Long userId);

    @Query("SELECT COUNT(th) > 0 FROM TrialHistory th WHERE th.userId = :userId")
    boolean hasTrialHistory(@Param("userId") Long userId);

    @Query("""
            SELECT th FROM TrialHistory th
            WHERE th.userId = :userId
            AND th.trialStartDate > :cutoffDate
            ORDER BY th.trialStartDate DESC
            """)
    List<TrialHistory> findRecentTrials(
            @Param("userId") Long userId,
            @Param("cutoffDate") LocalDateTime cutoffDate);

    // Only use device checks if deviceId is provided
    @Query("SELECT COUNT(th) > 0 FROM TrialHistory th WHERE th.deviceId = :deviceId")
    boolean existsByDeviceId(@Param("deviceId") String deviceId);
}