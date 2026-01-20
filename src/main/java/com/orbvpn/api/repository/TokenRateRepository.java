package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.TokenRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface TokenRateRepository extends JpaRepository<TokenRate, Long> {
    Optional<TokenRate> findByRegionAndAdVendor(String region, String adVendor);

    long deleteByRegionAndAdVendor(String region, String adVendor);

    List<TokenRate> findByRegion(String region);

}