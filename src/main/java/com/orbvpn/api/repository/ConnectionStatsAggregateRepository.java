package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.ConnectionStatsAggregate;
import com.orbvpn.api.domain.entity.MiningServer;
import com.orbvpn.api.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface ConnectionStatsAggregateRepository extends JpaRepository<ConnectionStatsAggregate, Long> {
    List<ConnectionStatsAggregate> findByUserAndPeriodAndAggregationDateBetween(
            User user,
            ConnectionStatsAggregate.AggregationPeriod period,
            LocalDateTime start,
            LocalDateTime end);

    List<ConnectionStatsAggregate> findByServerAndPeriodAndAggregationDateBetween(
            MiningServer server,
            ConnectionStatsAggregate.AggregationPeriod period,
            LocalDateTime start,
            LocalDateTime end);

    @Query("SELECT a FROM ConnectionStatsAggregate a " +
            "WHERE a.period = :period " +
            "AND a.aggregationDate BETWEEN :start AND :end " +
            "ORDER BY a.totalDataTransferred DESC")
    List<ConnectionStatsAggregate> findTopByDataTransferred(
            @Param("period") ConnectionStatsAggregate.AggregationPeriod period,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT a FROM ConnectionStatsAggregate a " +
            "WHERE a.period = :period " +
            "AND a.aggregationDate BETWEEN :start AND :end " +
            "ORDER BY a.totalTokensEarned DESC")
    List<ConnectionStatsAggregate> findTopByTokensEarned(
            @Param("period") ConnectionStatsAggregate.AggregationPeriod period,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT csa FROM ConnectionStatsAggregate csa " +
            "WHERE csa.period = :period " +
            "AND csa.aggregationDate BETWEEN :startDate AND :endDate")
    List<ConnectionStatsAggregate> findByPeriodAndAggregationDateBetween(
            @Param("period") ConnectionStatsAggregate.AggregationPeriod period,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

}