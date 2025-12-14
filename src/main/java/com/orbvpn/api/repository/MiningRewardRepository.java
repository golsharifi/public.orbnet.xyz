package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.MiningReward;
import com.orbvpn.api.domain.entity.MiningServer;
import com.orbvpn.api.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface MiningRewardRepository extends JpaRepository<MiningReward, Long> {

    List<MiningReward> findByUserAndRewardTimeAfter(User user, LocalDateTime since);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM MiningReward r " +
            "WHERE r.user = :user")
    BigDecimal sumRewardsByUser(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM MiningReward r " +
            "WHERE r.user = :user AND r.rewardTime >= :date")
    BigDecimal sumRewardsByUserAndDateAfter(@Param("user") User user, @Param("date") LocalDateTime date);

    @Query("SELECT COUNT(DISTINCT a.id) FROM MiningActivity a " +
            "WHERE a.user = :user AND a.isActive = true")
    int countActiveSessionsByUser(@Param("user") User user);

    @Query("SELECT DISTINCT r.server FROM MiningReward r " +
            "WHERE r.user = :user " +
            "GROUP BY r.server " +
            "ORDER BY SUM(r.amount) DESC " +
            "LIMIT 5")
    List<MiningServer> findTopServersByUser(@Param("user") User user);

    List<MiningReward> findByUserAndRewardTimeBetween(
            User user,
            LocalDateTime from,
            LocalDateTime to);

    @Query("SELECT COALESCE(AVG(CAST(r.amount AS double)), 0) FROM MiningReward r " +
            "WHERE r.user = :user " +
            "AND r.rewardTime >= FUNCTION('DATE_SUB', CURRENT_DATE, 7)")
    BigDecimal calculateAverageDailyReward(@Param("user") User user);

    List<MiningReward> findByUserOrderByRewardTimeDesc(User user);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM MiningReward r " +
            "WHERE r.user = :user " +
            "AND CAST(r.rewardTime AS date) = CURRENT_DATE")
    BigDecimal getTodayRewardsByUser(@Param("user") User user);

    @Query("SELECT r FROM MiningReward r " +
            "WHERE r.user = :user AND r.server.id = :serverId " +
            "ORDER BY r.rewardTime DESC")
    List<MiningReward> findByUserAndServerId(
            @Param("user") User user,
            @Param("serverId") Long serverId);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM MiningReward r " +
            "WHERE r.user = :user AND r.rewardTime >= :since")
    BigDecimal calculateTotalRewards(
            @Param("user") User user,
            @Param("since") LocalDateTime since);
}
