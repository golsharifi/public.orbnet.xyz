package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.TelegramMessage;
import com.orbvpn.api.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramMessageRepository extends JpaRepository<TelegramMessage, Long> {
    Page<TelegramMessage> findByUser(User user, Pageable pageable);

    Page<TelegramMessage> findAllByOrderByTimestampDesc(Pageable pageable);
}