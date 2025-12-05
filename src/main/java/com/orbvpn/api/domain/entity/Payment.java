package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.PaymentCategory;
import com.orbvpn.api.domain.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

import java.util.List;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.Version;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@ToString
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @ManyToOne
  @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "none"))
  private User user;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private PaymentStatus status;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private GatewayName gateway;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private PaymentCategory category;

  @Column
  private String paymentId;

  @Column(columnDefinition = "LONGTEXT")
  private String metaData;

  @Column
  @DecimalMin(value = "0.0", inclusive = true)
  private BigDecimal price;

  @Column
  private int groupId;

  @Column(name = "subscription_id")
  private String subscriptionId;

  @Column
  private int moreLoginCount;

  @Column
  private String errorMessage;

  @Column
  private boolean renew;

  @Column
  private boolean renewed;

  @Column
  private LocalDateTime expiresAt;

  @CreatedDate
  private LocalDateTime createdAt;

  @LastModifiedDate
  private LocalDateTime updatedAt;

  public String getSubscriptionId() {
    return subscriptionId;
  }

  public void setSubscriptionId(String subscriptionId) {
    this.subscriptionId = subscriptionId;
  }

  @OneToOne(mappedBy = "payment", cascade = CascadeType.ALL)
  private UserSubscription userSubscription;

  @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL)
  @ToString.Exclude
  @Builder.Default
  private List<UserSubscription> subscriptions = new ArrayList<>();

  @Version
  @Builder.Default
  private Long version = 0L;

  // Add these helper methods
  public void addSubscription(UserSubscription subscription) {
    subscriptions.add(subscription);
    subscription.setPayment(this);
  }

  public void removeSubscription(UserSubscription subscription) {
    subscriptions.remove(subscription);
    subscription.setPayment(null);
  }

}
