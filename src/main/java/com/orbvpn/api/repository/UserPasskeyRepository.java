package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserPasskey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPasskeyRepository extends JpaRepository<UserPasskey, Long> {

    List<UserPasskey> findByUser(User user);

    List<UserPasskey> findByUserId(Integer userId);

    Optional<UserPasskey> findByCredentialId(String credentialId);

    boolean existsByCredentialId(String credentialId);

    void deleteByIdAndUser(Long id, User user);

    long countByUser(User user);
}
