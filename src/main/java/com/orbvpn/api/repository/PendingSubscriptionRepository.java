package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.PendingSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.time.LocalDateTime;

public interface PendingSubscriptionRepository extends JpaRepository<PendingSubscription, Long> {
    Optional<PendingSubscription> findBySubscriptionId(String subscriptionId);

    Optional<PendingSubscription> findByPaymentId(Long paymentId);

    void deleteByProcessedAtBefore(LocalDateTime dateTime);

    long countBySubscriptionId(String subscriptionId);
}