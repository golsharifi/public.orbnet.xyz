package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.VerificationToken;
import com.orbvpn.api.domain.entity.UnverifiedUser;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Integer> {
    Optional<VerificationToken> findByVerificationCode(String verificationCode);
    Optional<VerificationToken> findByUser(UnverifiedUser user);

    // Added method to delete tokens by user
    void deleteByUser(UnverifiedUser user);
}
