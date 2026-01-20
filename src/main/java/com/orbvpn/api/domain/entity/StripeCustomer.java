package com.orbvpn.api.domain.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stripe_customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StripeCustomer {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "stripe_id")
  private String stripeId;

  @Column(name = "default_payment_method_id")
  private String defaultPaymentMethodId;

  @Column(name = "subscription_id")
  private String subscriptionId;

  @Column(name = "subscription_status")
  private String subscriptionStatus;

  @Column(name = "current_period_end")
  private LocalDateTime currentPeriodEnd;

  @Column(name = "cancel_at_period_end")
  private Boolean cancelAtPeriodEnd;

  @Column(name = "trial_end")
  private LocalDateTime trialEnd;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  // Helper methods
  public boolean hasActiveSubscription() {
    return subscriptionId != null &&
        currentPeriodEnd != null &&
        currentPeriodEnd.isAfter(LocalDateTime.now());
  }

  public boolean isInTrialPeriod() {
    return trialEnd != null &&
        trialEnd.isAfter(LocalDateTime.now());
  }
}