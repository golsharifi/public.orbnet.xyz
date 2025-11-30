package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.MessageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {
    Optional<MessageTemplate> findByName(String name);
}