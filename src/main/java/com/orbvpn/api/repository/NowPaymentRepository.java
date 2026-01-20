package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.NowPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NowPaymentRepository extends JpaRepository<NowPayment, Long> {

    /**
     * Find payment by NOWPayments payment ID
     */
    Optional<NowPayment> findByPaymentId(String paymentId);

    /**
     * Find payment by internal order ID
     */
    Optional<NowPayment> findByOrderId(String orderId);

    /**
     * Find pending payments that may have expired
     */
    @Query("SELECT np FROM NowPayment np WHERE np.paymentStatus IN ('waiting', 'confirming') " +
           "AND np.createdAt < :threshold")
    List<NowPayment> findPendingPaymentsOlderThan(@Param("threshold") LocalDateTime threshold);

    /**
     * Find payments by status
     */
    List<NowPayment> findByPaymentStatus(String paymentStatus);

    /**
     * Find payments by user ID
     */
    List<NowPayment> findByUserId(Integer userId);

    /**
     * Check if a payment with this NOWPayments ID already exists
     */
    boolean existsByPaymentId(String paymentId);
}
