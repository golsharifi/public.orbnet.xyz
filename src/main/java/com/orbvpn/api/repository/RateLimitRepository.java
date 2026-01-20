package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.RateLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RateLimitRepository extends JpaRepository<RateLimit, Integer> {
    Optional<RateLimit> findByEmail(String email);
}
