package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.ProcessedNowPaymentWebhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessedNowPaymentWebhookRepository extends JpaRepository<ProcessedNowPaymentWebhook, Long> {

    /**
     * Check if a webhook for this payment ID and status has already been processed.
     * This is the key idempotency check.
     */
    boolean existsByPaymentIdAndPaymentStatus(String paymentId, String paymentStatus);

    /**
     * Find a processed webhook by payment ID and status
     */
    Optional<ProcessedNowPaymentWebhook> findByPaymentIdAndPaymentStatus(String paymentId, String paymentStatus);

    /**
     * Find all webhooks for a payment ID
     */
    List<ProcessedNowPaymentWebhook> findByPaymentIdOrderByProcessedAtDesc(String paymentId);

    /**
     * Find webhooks by order ID
     */
    List<ProcessedNowPaymentWebhook> findByOrderIdOrderByProcessedAtDesc(String orderId);

    /**
     * Find webhooks processed before a certain date (for cleanup)
     */
    List<ProcessedNowPaymentWebhook> findByProcessedAtBefore(LocalDateTime date);

    /**
     * Delete old processed webhooks (for cleanup job)
     */
    @Modifying
    @Query("DELETE FROM ProcessedNowPaymentWebhook p WHERE p.processedAt < :cutoffDate")
    int deleteByProcessedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find failed webhooks for retry
     */
    List<ProcessedNowPaymentWebhook> findByStatusOrderByProcessedAtDesc(String status);

    /**
     * Count webhooks by status
     */
    long countByStatus(String status);

    /**
     * Find the latest webhook for a payment ID
     */
    Optional<ProcessedNowPaymentWebhook> findTopByPaymentIdOrderByProcessedAtDesc(String paymentId);
}
