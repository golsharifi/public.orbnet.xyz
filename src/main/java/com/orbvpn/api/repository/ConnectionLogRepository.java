package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.ConnectionLog;
import com.orbvpn.api.domain.entity.MiningServer;
import com.orbvpn.api.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ConnectionLogRepository extends JpaRepository<ConnectionLog, Long> {
    List<ConnectionLog> findByUserAndServerAndTimestampBetween(
            User user,
            MiningServer server,
            LocalDateTime startTime,
            LocalDateTime endTime);
}
