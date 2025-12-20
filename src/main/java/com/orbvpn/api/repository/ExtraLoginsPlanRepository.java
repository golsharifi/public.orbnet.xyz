package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.ExtraLoginsPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExtraLoginsPlanRepository extends JpaRepository<ExtraLoginsPlan, Long> {
    List<ExtraLoginsPlan> findBySubscriptionAndActive(boolean subscription, boolean active);

    List<ExtraLoginsPlan> findByGiftableAndActive(boolean giftable, boolean active);

    @Query("SELECT p FROM ExtraLoginsPlan p WHERE p.durationDays > 0 AND p.active = true")
    List<ExtraLoginsPlan> findTemporaryPlans();

    @Query("SELECT p FROM ExtraLoginsPlan p WHERE p.subscription = false AND p.durationDays = 0 AND p.active = true")
    List<ExtraLoginsPlan> findPermanentPlans();
}