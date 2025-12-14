package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.YandexPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Yandex Pay payment entities.
 */
@Repository
public interface YandexPaymentRepository extends JpaRepository<YandexPayment, Long> {

    /**
     * Find by Yandex Pay order ID
     */
    Optional<YandexPayment> findByYandexOrderId(String yandexOrderId);

    /**
     * Find by internal order ID
     */
    Optional<YandexPayment> findByOrderId(String orderId);

    /**
     * Find by operation ID
     */
    Optional<YandexPayment> findByOperationId(String operationId);

    /**
     * Check if order exists by Yandex order ID
     */
    boolean existsByYandexOrderId(String yandexOrderId);

    /**
     * Check if order exists by internal order ID
     */
    boolean existsByOrderId(String orderId);

    /**
     * Find all payments by user ID
     */
    List<YandexPayment> findByUserId(Integer userId);

    /**
     * Find pending payments older than threshold (for cleanup/expiration)
     */
    @Query("SELECT yp FROM YandexPayment yp WHERE yp.paymentStatus = 'PENDING' AND yp.createdAt < :threshold")
    List<YandexPayment> findPendingPaymentsOlderThan(LocalDateTime threshold);

    /**
     * Find by payment reference ID
     */
    Optional<YandexPayment> findByPaymentId(Integer paymentId);
}
