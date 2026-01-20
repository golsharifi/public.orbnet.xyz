package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.TokenStake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;
import java.math.BigDecimal;

public interface TokenStakeRepository extends JpaRepository<TokenStake, Long> {
    List<TokenStake> findByUnstakedAtIsNull();

    List<TokenStake> findByUserIdAndUnstakedAtIsNull(int userId);

    @Query("SELECT ts FROM TokenStake ts WHERE ts.user.id = :userId AND ts.unstakedAt IS NULL AND ts.stakedAt <= :beforeDate")
    List<TokenStake> findActiveStakesByUserAndDate(@Param("userId") Long userId,
            @Param("beforeDate") LocalDateTime beforeDate);

    @Query("SELECT SUM(ts.amount) FROM TokenStake ts WHERE ts.user.id = :userId AND ts.unstakedAt IS NULL")
    BigDecimal getTotalStakedAmount(@Param("userId") Long userId);
}