package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.MiningSettingsEntity;
import com.orbvpn.api.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface MiningSettingsRepository extends JpaRepository<MiningSettingsEntity, Long> {
    Optional<MiningSettingsEntity> findByUser(User user);

    List<MiningSettingsEntity> findByAutoWithdrawTrue();

    boolean existsByUserAndAutoWithdrawTrue(User user);

    @Query("SELECT ms FROM MiningSettingsEntity ms " +
            "WHERE ms.autoWithdraw = true " +
            "AND ms.withdrawAddress IS NOT NULL " +
            "AND ms.minWithdrawAmount IS NOT NULL")
    List<MiningSettingsEntity> findAllValidAutoWithdrawSettings();
}