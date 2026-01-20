package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.WebhookDeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WebhookDeliveryAttemptRepository extends JpaRepository<WebhookDeliveryAttempt, Long> {
    List<WebhookDeliveryAttempt> findByDeliveryId(Long deliveryId);
}