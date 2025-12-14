package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.CongestionLevel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CongestionLevelRepository extends JpaRepository<CongestionLevel, Integer> {
}
