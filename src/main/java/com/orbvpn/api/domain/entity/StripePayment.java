package com.orbvpn.api.domain.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stripe_payment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StripePayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne
    @JoinColumn(name = "stripe_customer_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "FK_stripe_payment_stripe_customer"))
    private StripeCustomer stripeCustomer;

    @Column(name = "payment_intent_id")
    private String paymentIntentId;

    @Column(name = "payment_method_id")
    private String paymentMethodId;

    @Column(name = "subscription_id")
    private String subscriptionId;

    @Column(name = "invoice_id")
    private String invoiceId;

    @Column(name = "subscription_status")
    private String subscriptionStatus;

    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "cancel_at_period_end")
    private Boolean cancelAtPeriodEnd;

    @Column(name = "latest_invoice_id")
    private String latestInvoiceId;

    @Column(name = "trial_end")
    private LocalDateTime trialEnd;

    @Column(name = "stripe_status")
    private String stripeStatus;

    @Column(name = "stripe_error")
    private String stripeError;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // Helper methods
    public boolean isSubscription() {
        return subscriptionId != null;
    }

    public boolean requiresAction() {
        return "requires_action".equals(stripeStatus) ||
                "requires_payment_method".equals(stripeStatus);
    }

    public boolean isSucceeded() {
        return "succeeded".equals(stripeStatus);
    }

    public boolean isActive() {
        return currentPeriodEnd != null &&
                currentPeriodEnd.isAfter(LocalDateTime.now());
    }

    public boolean isInTrialPeriod() {
        return trialEnd != null &&
                trialEnd.isAfter(LocalDateTime.now());
    }
}