package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.MiningActivity;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.MiningServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MiningActivityRepository extends JpaRepository<MiningActivity, Long> {
    Optional<MiningActivity> findByUserAndServerAndIsActiveTrue(User user, MiningServer server);

    List<MiningActivity> findByUserAndEndTimeAfter(User user, LocalDateTime since);

    @Query("SELECT SUM(ma.dataTransferred) FROM MiningActivity ma " +
            "WHERE ma.user = :user AND ma.server = :server AND ma.endTime >= :since")
    BigDecimal calculateTotalDataTransferred(@Param("user") User user,
            @Param("server") MiningServer server,
            @Param("since") LocalDateTime since);

    List<MiningActivity> findByUserAndIsActiveTrue(User user);

    List<MiningActivity> findAllByUserAndIsActiveTrue(User user);

    Optional<MiningActivity> findFirstByUserAndIsActiveTrue(User user);

}