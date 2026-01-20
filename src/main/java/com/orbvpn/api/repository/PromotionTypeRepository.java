package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.PromotionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionTypeRepository extends JpaRepository<PromotionType, Integer> {
    PromotionType findPromotionTypeByName(String name);
}
