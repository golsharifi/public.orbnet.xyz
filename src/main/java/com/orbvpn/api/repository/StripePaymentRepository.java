package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.StripePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface StripePaymentRepository extends JpaRepository<StripePayment, Long> {
    @Query("SELECT p FROM Payment p WHERE p.paymentId = :paymentIntentId")
    Optional<Payment> findByStripePaymentIntentId(String paymentIntentId);

    Optional<StripePayment> findByPaymentIntentId(String paymentIntentId);

    Optional<StripePayment> findBySubscriptionId(String subscriptionId);

    Optional<StripePayment> findByPayment_Id(Long paymentId);

    Optional<StripePayment> findByInvoiceId(String invoiceId);
}