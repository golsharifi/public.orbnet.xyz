package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.TokenTransaction;
import com.orbvpn.api.domain.enums.TokenTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TokenTransactionRepository extends JpaRepository<TokenTransaction, Long> {

        @Query("SELECT COUNT(t) FROM TokenTransaction t WHERE t.user.id = :userId " +
                        "AND t.type = 'EARN' AND t.createdAt BETWEEN :start AND :end")
        int countAdsWatched(
                        @Param("userId") Integer userId,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        List<TokenTransaction> findByUser_IdOrderByCreatedAtDesc(Integer userId);

        @Query("SELECT SUM(t.amount) FROM TokenTransaction t WHERE t.user.id = :userId " +
                        "AND t.type = :type AND t.createdAt BETWEEN :start AND :end")
        BigDecimal sumAmountByTypeAndDateRange(
                        @Param("userId") Integer userId,
                        @Param("type") TokenTransactionType type,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        @Query("SELECT COUNT(DISTINCT t.user.id) FROM TokenTransaction t " +
                        "WHERE t.createdAt BETWEEN :start AND :end")
        long countDistinctUsersByDateRange(
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        @Query("SELECT SUM(t.amount) FROM TokenTransaction t " +
                        "WHERE t.type = :type AND t.createdAt BETWEEN :start AND :end")
        BigDecimal sumTotalAmountByTypeAndDateRange(
                        @Param("type") TokenTransactionType type,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        @Query("SELECT COUNT(t) FROM TokenTransaction t " +
                        "WHERE t.type = 'EARN' AND t.createdAt BETWEEN :start AND :end")
        int countTotalAdsWatched(
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        @Query("SELECT COUNT(t) FROM TokenTransaction t " +
                        "WHERE t.type = 'EARN' AND t.user.id = :userId")
        int countLifetimeAdsWatched(@Param("userId") Integer userId);

        List<TokenTransaction> findByUserIdAndCreatedAtBetween(
                        Integer userId,
                        LocalDateTime startDate,
                        LocalDateTime endDate);
}