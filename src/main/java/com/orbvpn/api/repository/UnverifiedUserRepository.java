package com.orbvpn.api.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.orbvpn.api.domain.entity.UnverifiedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnverifiedUserRepository extends JpaRepository<UnverifiedUser, Integer> {

    Optional<UnverifiedUser> findByEmail(String email);

    // Method to find all UnverifiedUser entries older than a given date
    @Query("SELECT u FROM UnverifiedUser u WHERE u.createdAt < :dateTime")
    List<UnverifiedUser> findAllByCreatedAtBefore(@Param("dateTime") LocalDateTime dateTime);

    // ... other methods
}
