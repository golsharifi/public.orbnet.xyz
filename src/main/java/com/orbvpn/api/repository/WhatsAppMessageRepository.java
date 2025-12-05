package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.WhatsAppMessage;
import com.orbvpn.api.domain.entity.WhatsAppMessage.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for WhatsApp message tracking.
 */
@Repository
public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, Long> {

    /**
     * Find messages by phone number.
     */
    List<WhatsAppMessage> findByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);

    /**
     * Find messages by status.
     */
    List<WhatsAppMessage> findByStatusOrderByCreatedAtDesc(MessageStatus status);

    /**
     * Find pending messages for retry.
     */
    @Query("SELECT m FROM WhatsAppMessage m WHERE m.status = 'PENDING' AND m.retryCount < :maxRetries ORDER BY m.createdAt ASC")
    List<WhatsAppMessage> findPendingForRetry(@Param("maxRetries") int maxRetries);

    /**
     * Find failed messages within a time range.
     */
    List<WhatsAppMessage> findByStatusAndFailedAtAfter(MessageStatus status, LocalDateTime since);

    /**
     * Find messages by user.
     */
    Page<WhatsAppMessage> findByUserIdOrderByCreatedAtDesc(int userId, Pageable pageable);

    /**
     * Count messages by status.
     */
    long countByStatus(MessageStatus status);

    /**
     * Count messages sent to a phone number within a time range.
     */
    @Query("SELECT COUNT(m) FROM WhatsAppMessage m WHERE m.phoneNumber = :phone AND m.createdAt > :since")
    long countRecentByPhoneNumber(@Param("phone") String phoneNumber, @Param("since") LocalDateTime since);

    /**
     * Get message statistics.
     */
    @Query("SELECT m.status, COUNT(m) FROM WhatsAppMessage m WHERE m.createdAt > :since GROUP BY m.status")
    List<Object[]> getStatusStatsSince(@Param("since") LocalDateTime since);

    /**
     * Find messages created within a time range.
     */
    List<WhatsAppMessage> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    /**
     * Delete old messages (for cleanup).
     */
    void deleteByCreatedAtBefore(LocalDateTime before);
}
