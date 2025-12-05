package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.NotificationStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationStatsRepository extends JpaRepository<NotificationStats, Long> {
    List<NotificationStats> findByStatDateBetween(LocalDate startDate, LocalDate endDate);

    List<NotificationStats> findByStatDate(LocalDate date);
}
