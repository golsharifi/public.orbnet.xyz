package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.ProcessedCoinPaymentWebhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessedCoinPaymentWebhookRepository extends JpaRepository<ProcessedCoinPaymentWebhook, Long> {

    /**
     * Check if a webhook with this IPN ID has already been processed
     */
    boolean existsByIpnId(String ipnId);

    /**
     * Find a processed webhook by its IPN ID
     */
    Optional<ProcessedCoinPaymentWebhook> findByIpnId(String ipnId);

    /**
     * Find all webhooks for a specific payment
     */
    List<ProcessedCoinPaymentWebhook> findByPaymentIdOrderByProcessedAtDesc(Long paymentId);

    /**
     * Find webhooks processed before a certain date (for cleanup)
     */
    List<ProcessedCoinPaymentWebhook> findByProcessedAtBefore(LocalDateTime date);

    /**
     * Delete old processed webhooks (for cleanup job)
     */
    @Modifying
    @Query("DELETE FROM ProcessedCoinPaymentWebhook p WHERE p.processedAt < :cutoffDate")
    int deleteByProcessedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count webhooks by payment ID
     */
    long countByPaymentId(Long paymentId);
}
