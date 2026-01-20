package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.TokenBalance;
import com.orbvpn.api.domain.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface TokenBalanceRepository extends JpaRepository<TokenBalance, Long> {
    // Change findByUserId to findByUser_Id
    Optional<TokenBalance> findByUser_Id(Integer userId);

    Optional<TokenBalance> findByUser(User user);

    /**
     * Find TokenBalance by user with pessimistic write lock.
     * Use this for all balance modifications to prevent race conditions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tb FROM TokenBalance tb WHERE tb.user = :user")
    Optional<TokenBalance> findByUserWithLock(@Param("user") User user);

    /**
     * Find TokenBalance by user ID with pessimistic write lock.
     * Use this for all balance modifications to prevent race conditions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tb FROM TokenBalance tb WHERE tb.user.id = :userId")
    Optional<TokenBalance> findByUserIdWithLock(@Param("userId") Integer userId);

    // Sum all balances
    @Query("SELECT COALESCE(SUM(tb.balance), 0) FROM TokenBalance tb")
    BigDecimal sumAllBalances();
}