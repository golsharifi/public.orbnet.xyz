package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.TokenStakingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface TokenStakingConfigRepository extends JpaRepository<TokenStakingConfig, Long> {
    List<TokenStakingConfig> findByIsActiveTrue();

    @Query("SELECT tsc FROM TokenStakingConfig tsc " +
            "WHERE tsc.isActive = true " +
            "AND tsc.minimumStakeAmount <= :amount " +
            "AND (tsc.maximumStakeAmount IS NULL OR tsc.maximumStakeAmount >= :amount) " +
            "ORDER BY tsc.baseApy DESC")
    List<TokenStakingConfig> findAvailableTiersForAmount(@Param("amount") BigDecimal amount);

    @Query("SELECT tsc FROM TokenStakingConfig tsc " +
            "JOIN TokenStakingTierRequirement tr ON tr.stakingConfig = tsc " +
            "WHERE tsc.isActive = true " +
            "AND tr.requirementType = :reqType " +
            "AND tr.requirementValue = :reqValue")
    List<TokenStakingConfig> findByRequirement(
            @Param("reqType") String requirementType,
            @Param("reqValue") String requirementValue);
}