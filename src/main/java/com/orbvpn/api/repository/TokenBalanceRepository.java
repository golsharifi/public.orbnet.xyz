package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.TokenBalance;
import com.orbvpn.api.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Optional;

public interface TokenBalanceRepository extends JpaRepository<TokenBalance, Long> {
    // Change findByUserId to findByUser_Id
    Optional<TokenBalance> findByUser_Id(Integer userId);

    Optional<TokenBalance> findByUser(User user);

    // Sum all balances
    @Query("SELECT COALESCE(SUM(tb.balance), 0) FROM TokenBalance tb")
    BigDecimal sumAllBalances();
}