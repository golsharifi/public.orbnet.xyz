package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.SubscriptionTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionTransactionRepository extends JpaRepository<SubscriptionTransaction, Long> {

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM SubscriptionTransaction t WHERE t.transactionId = :transactionId AND t.type = :type")
    boolean existsByTransactionIdAndType(@Param("transactionId") String transactionId, @Param("type") String type);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM SubscriptionTransaction t WHERE t.transactionId = :transactionId")
    boolean existsByTransactionId(@Param("transactionId") String transactionId);

    @Modifying
    @Query("DELETE FROM SubscriptionTransaction t WHERE t.processedAt < :cutoff")
    int deleteByProcessedAtBefore(@Param("cutoff") LocalDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM SubscriptionTransaction t WHERE t.transactionId = :transactionId AND t.type = :type")
    Optional<SubscriptionTransaction> findWithLockByTransactionIdAndType(
            @Param("transactionId") String transactionId,
            @Param("type") String type);

    @Query(value = """
            SELECT t.* FROM subscription_transactions t
            WHERE t.processed_at < :cutoff
            AND NOT EXISTS (
                SELECT 1 FROM user_subscription s
                WHERE s.purchase_token = t.transaction_id
                OR s.original_transaction_id = t.transaction_id
            )
            """, nativeQuery = true)
    List<SubscriptionTransaction> findOrphanedTransactions(@Param("cutoff") LocalDateTime cutoff);
}