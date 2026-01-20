package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {
    Page<WebhookDelivery> findByWebhookId(Long webhookId, Pageable pageable);

    List<WebhookDelivery> findByStatus(String status);

    Page<WebhookDelivery> findByWebhookIdAndStatus(Long webhookId, String status, Pageable pageable);
}