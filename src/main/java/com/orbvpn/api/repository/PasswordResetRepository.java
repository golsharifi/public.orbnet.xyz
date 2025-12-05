package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.PasswordReset;
import com.orbvpn.api.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, String> {
    Long deleteByUserAndTokenNot(User user, String token);

    void deleteAllByUser(User user);

    Optional<PasswordReset> findByUser(User user);
}
