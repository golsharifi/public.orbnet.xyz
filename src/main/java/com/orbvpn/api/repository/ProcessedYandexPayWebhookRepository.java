package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.ProcessedYandexPayWebhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessedYandexPayWebhookRepository extends JpaRepository<ProcessedYandexPayWebhook, Long> {

    /**
     * Check if this exact order + event + status combination was already processed.
     * This is the key idempotency check.
     */
    boolean existsByOrderIdAndEventAndPaymentStatus(String orderId, String event, String paymentStatus);

    /**
     * Find a processed webhook by order ID, event, and status
     */
    Optional<ProcessedYandexPayWebhook> findByOrderIdAndEventAndPaymentStatus(
            String orderId, String event, String paymentStatus);

    /**
     * Find all webhooks for an order ID
     */
    List<ProcessedYandexPayWebhook> findByOrderIdOrderByProcessedAtDesc(String orderId);

    /**
     * Find webhooks by Yandex order ID
     */
    List<ProcessedYandexPayWebhook> findByYandexOrderIdOrderByProcessedAtDesc(String yandexOrderId);

    /**
     * Find webhooks processed before a certain date (for cleanup)
     */
    List<ProcessedYandexPayWebhook> findByProcessedAtBefore(LocalDateTime date);

    /**
     * Delete old processed webhooks (for cleanup job)
     */
    @Modifying
    @Query("DELETE FROM ProcessedYandexPayWebhook p WHERE p.processedAt < :cutoffDate")
    int deleteByProcessedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find failed webhooks for retry
     */
    List<ProcessedYandexPayWebhook> findByStatusOrderByProcessedAtDesc(String status);

    /**
     * Count webhooks by status
     */
    long countByStatus(String status);

    /**
     * Find the latest webhook for an order ID
     */
    Optional<ProcessedYandexPayWebhook> findTopByOrderIdOrderByProcessedAtDesc(String orderId);
}
