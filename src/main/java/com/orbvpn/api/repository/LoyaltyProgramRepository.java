package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.LoyaltyProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoyaltyProgramRepository extends JpaRepository<LoyaltyProgram, Long> {
    @Query("SELECT lp FROM LoyaltyProgram lp WHERE lp.minAccountAgeDays <= :days AND lp.active = true ORDER BY lp.discountPercent DESC")
    List<LoyaltyProgram> findApplicableLoyaltyPrograms(int days);

    Optional<LoyaltyProgram> findByNameAndActive(String name, boolean active);

    List<LoyaltyProgram> findByMinAccountAgeDaysLessThanEqual(int accountAgeDays);

}