package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.ReferralCode;
import com.orbvpn.api.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralCodeRepository extends JpaRepository<ReferralCode, Integer> {
    ReferralCode findReferralCodeByCode(String code);
    void deleteByUser(User user);
}
