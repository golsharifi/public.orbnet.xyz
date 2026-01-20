package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.PaymentStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

  @Query("select payment from Payment payment where payment.gateway = :gateway and payment.paymentId = :paymentId")
  Optional<Payment> findByGatewayAndPaymentId(GatewayName gateway, String paymentId);

  Optional<Payment> findByPaymentId(String paymentId);

  List<Payment> findByStatus(PaymentStatus status);

  @Query("SELECT p FROM Payment p WHERE p.paymentId = :paymentIntentId")
  Optional<Payment> findByStripePaymentIntentId(String paymentIntentId);

  @Query("SELECT p FROM Payment p WHERE p.status = 'PENDING' AND p.expiresAt <= :now")
  List<Payment> findAllSubscriptionPaymentsToRenew(@Param("now") LocalDateTime now);

  @Query("select count(payment.id) from Payment payment where payment.createdAt > :createdAt and payment.renewed = true and payment.category = 'GROUP'")
  int getTotalRenewSubscriptionCount(LocalDateTime createdAt);

  @Query("select sum (payment.price) from Payment payment where payment.createdAt > :createdAt and payment.renewed = true and payment.category = 'GROUP'")
  BigDecimal getTotalRenewSubscriptionPrice(LocalDateTime createdAt);

  void deleteByUser(User user);

  @Query("SELECT p FROM Payment p WHERE p.status = 'PENDING' AND p.createdAt < :expireTime")
  List<Payment> findExpiredPendingPayments(@Param("expireTime") LocalDateTime expireTime);

  @Query("SELECT p FROM Payment p WHERE p.gateway = :gateway AND p.status = 'PENDING' AND p.expiresAt <= :now")
  List<Payment> findAllSubscriptionPaymentsToRenewByGateway(
      @Param("gateway") GatewayName gateway,
      @Param("now") LocalDateTime now);

  @Modifying
  @Query(value = "DELETE FROM payment WHERE user_id = :userId", nativeQuery = true)
  void deleteByUserIdNative(@Param("userId") long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Payment p WHERE p.id = :id")
  Optional<Payment> findByIdWithLock(@Param("id") Integer id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Payment p WHERE p.gateway = :gateway AND p.paymentId = :paymentId")
  Optional<Payment> findByGatewayAndPaymentIdWithLock(
      @Param("gateway") GatewayName gateway,
      @Param("paymentId") String paymentId);

  /**
   * Check if a payment already exists with the given payment ID and gateway.
   * Used for idempotency checks on IAP transactions.
   */
  @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Payment p " +
         "WHERE p.paymentId = :paymentId AND p.gateway = :gateway")
  boolean existsByPaymentIdAndGateway(
      @Param("paymentId") String paymentId,
      @Param("gateway") GatewayName gateway);
}
