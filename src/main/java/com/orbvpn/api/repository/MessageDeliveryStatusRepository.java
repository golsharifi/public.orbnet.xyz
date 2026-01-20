package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.MessageDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MessageDeliveryStatusRepository extends JpaRepository<MessageDeliveryStatus, Long> {
    Optional<MessageDeliveryStatus> findByMessageId(String messageId);

    List<MessageDeliveryStatus> findByUserIdOrderBySentAtDesc(String userId);

    List<MessageDeliveryStatus> findByStatus(String status);
}