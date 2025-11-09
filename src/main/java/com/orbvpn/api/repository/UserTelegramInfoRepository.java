package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.UserTelegramInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTelegramInfoRepository extends JpaRepository<UserTelegramInfo, Long> {
    Optional<UserTelegramInfo> findByUser_Id(Long userId);

    Optional<UserTelegramInfo> findByTelegramUsername(String username);
}