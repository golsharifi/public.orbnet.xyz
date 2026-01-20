package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.SubscriptionStatus;
import com.orbvpn.api.domain.enums.PriceIncreaseStatus;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.orbvpn.api.domain.enums.GatewayName;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.math.RoundingMode;

@Entity
@Getter
@Setter
@Slf4j
@EntityListeners(AuditingEntityListener.class)
public class UserSubscription {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @ManyToOne
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @NotFound(action = NotFoundAction.IGNORE)
  private Group group;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payment_id")
  private Payment payment;

  @Column(name = "pending_group_id")
  private Integer pendingGroupId;

  @Column(name = "price", precision = 19, scale = 4)
  private BigDecimal price;

  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "price_increase_status")
  @Enumerated(EnumType.STRING)
  private PriceIncreaseStatus priceIncreaseStatus = PriceIncreaseStatus.NONE;

  @Column
  @PositiveOrZero
  private int duration;

  @Column(name = "is_trial_period")
  private Boolean isTrialPeriod;

  @Column
  @DecimalMin(value = "0")
  private BigInteger dailyBandwidth;

  @Column
  private int multiLoginCount;

  @Column
  @DecimalMin(value = "0")
  private BigInteger downloadUpload;

  // For subscriptions we have expiration data big value
  @Column
  private LocalDateTime expiresAt;

  // Change the field from primitive `boolean` to wrapper class `Boolean`
  @Column
  private Boolean canceled = false;

  @Column
  private Boolean autoRenew = true;

  // Add fields for purchaseToken (Google Play) and originalTransactionId (Apple)
  @Column
  private String purchaseToken;

  @Column
  private String originalTransactionId;

  @Column
  @CreatedDate
  private LocalDateTime createdAt;

  @Column
  @LastModifiedDate
  private LocalDateTime updatedAt;

  @Column(name = "status")
  @Enumerated(EnumType.STRING)
  private SubscriptionStatus status = SubscriptionStatus.PENDING;

  @Column(name = "subscription_id")
  private String subscriptionId;

  @Column(name = "acknowledged")
  private Boolean acknowledged = false;

  @Column(name = "receipt_data", columnDefinition = "MEDIUMTEXT")
  private String receiptData;

  @Column(name = "trial_end_date")
  private LocalDateTime trialEndDate;

  @Column(name = "is_token_based")
  private Boolean isTokenBased = false;

  @Column(name = "weekly_ads_watched")
  private Integer weeklyAdsWatched = 0;

  @Column(name = "last_weekly_reset")
  private LocalDateTime lastWeeklyReset;

  @Column(name = "stripe_customer_id")
  private String stripeCustomerId;

  @Column(name = "stripe_subscription_id")
  private String stripeSubscriptionId;

  @Column(name = "payment_state")
  private Integer paymentState;

  @Column(name = "acknowledgement_state")
  private Integer acknowledgementState;

  @Column(name = "grace_period_ends_at")
  private LocalDateTime gracePeriodEndsAt;

  @Column(name = "failed_payment_attempts")
  private Integer failedPaymentAttempts = 0;

  @Column(name = "order_id", length = 50)
  private String orderId;

  // ========== BANDWIDTH TRACKING ==========

  /**
   * Total bandwidth used in bytes (across all protocols).
   * Updated when connections end.
   */
  @Column(name = "bandwidth_used_bytes")
  private Long bandwidthUsedBytes = 0L;

  /**
   * Bandwidth quota in bytes (base from plan + purchased addons).
   * Null means unlimited.
   */
  @Column(name = "bandwidth_quota_bytes")
  private Long bandwidthQuotaBytes;

  /**
   * Additional bandwidth purchased as addons (in bytes).
   */
  @Column(name = "bandwidth_addon_bytes")
  private Long bandwidthAddonBytes = 0L;

  /**
   * Monthly bandwidth limit reset date.
   * If set, bandwidthUsedBytes resets on this date each month.
   */
  @Column(name = "bandwidth_reset_date")
  private LocalDateTime bandwidthResetDate;

  @Transient
  private Boolean cancelAtPeriodEnd = false;

  @Version
  @Column(name = "version")
  private Long version = 0L;

  @Enumerated(EnumType.STRING)
  private GatewayName gateway;

  public UserSubscription extendDuration(int days) {
    this.duration += days;
    this.expiresAt = this.expiresAt.plusDays(days);
    return this;
  }

  // Set the subscription as canceled
  public void setCanceled(Boolean canceled) {
    this.canceled = canceled;
  }

  public Boolean isCanceled() {
    return this.canceled;
  }

  public Boolean getIsTrialPeriod() {
    return isTrialPeriod;
  }

  public void setIsTrialPeriod(Boolean isTrialPeriod) {
    this.isTrialPeriod = isTrialPeriod;
  }

  public Integer getPendingGroupId() {
    return pendingGroupId;
  }

  public void setPendingGroupId(Integer pendingGroupId) {
    this.pendingGroupId = pendingGroupId;
  }

  public boolean isValid() {
    if (Boolean.TRUE.equals(isTokenBased)) {
      return weeklyAdsWatched >= getGroup().getMinWeeklyAds()
          && lastWeeklyReset.plusWeeks(1).isAfter(LocalDateTime.now());
    }
    // Implement the default validity check here
    return expiresAt != null && expiresAt.isAfter(LocalDateTime.now());
  }

  public void setGateway(GatewayName gateway) {
    this.gateway = gateway;
  }

  public GatewayName getGateway() {
    return gateway;
  }

  @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<SubscriptionHistory> subscriptionHistories = new HashSet<>();

  public Set<SubscriptionHistory> getSubscriptionHistories() {
    return subscriptionHistories;
  }

  public void setSubscriptionHistories(Set<SubscriptionHistory> subscriptionHistories) {
    this.subscriptionHistories = subscriptionHistories;
  }

  public void addSubscriptionHistory(SubscriptionHistory history) {
    subscriptionHistories.add(history);
    history.setSubscription(this);
  }

  public void removeSubscriptionHistory(SubscriptionHistory history) {
    subscriptionHistories.remove(history);
    history.setSubscription(null);
  }

  public String getReceiptData() {
    return receiptData;
  }

  public void setReceiptData(String receiptData) {
    this.receiptData = receiptData;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    if (price != null) {
      // Ensure proper scale
      this.price = price.setScale(4, RoundingMode.HALF_UP);
      log.debug("Setting price to: {}", this.price);
    } else {
      this.price = null;
    }
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
    log.debug("Setting currency to: {}", currency);
  }

  public Boolean getCancelAtPeriodEnd() {
    return cancelAtPeriodEnd;
  }

  public void setCancelAtPeriodEnd(Boolean cancelAtPeriodEnd) {
    this.cancelAtPeriodEnd = cancelAtPeriodEnd;
  }

  // ========== BANDWIDTH HELPER METHODS ==========

  /**
   * Get total bandwidth quota (base + addons).
   * Returns null if unlimited.
   */
  public Long getTotalBandwidthQuota() {
    if (bandwidthQuotaBytes == null) {
      return null; // Unlimited
    }
    long addon = bandwidthAddonBytes != null ? bandwidthAddonBytes : 0L;
    return bandwidthQuotaBytes + addon;
  }

  /**
   * Get remaining bandwidth in bytes.
   * Returns null if unlimited, negative if exceeded.
   */
  public Long getRemainingBandwidth() {
    Long totalQuota = getTotalBandwidthQuota();
    if (totalQuota == null) {
      return null; // Unlimited
    }
    long used = bandwidthUsedBytes != null ? bandwidthUsedBytes : 0L;
    return totalQuota - used;
  }

  /**
   * Check if bandwidth limit is exceeded.
   */
  public boolean isBandwidthExceeded() {
    Long remaining = getRemainingBandwidth();
    return remaining != null && remaining <= 0;
  }

  /**
   * Get bandwidth usage percentage (0-100+).
   * Returns 0 if unlimited.
   */
  public double getBandwidthUsagePercent() {
    Long totalQuota = getTotalBandwidthQuota();
    if (totalQuota == null || totalQuota == 0) {
      return 0.0;
    }
    long used = bandwidthUsedBytes != null ? bandwidthUsedBytes : 0L;
    return (used * 100.0) / totalQuota;
  }

  /**
   * Add bandwidth usage.
   */
  public void addBandwidthUsage(long bytes) {
    if (bandwidthUsedBytes == null) {
      bandwidthUsedBytes = 0L;
    }
    bandwidthUsedBytes += bytes;
  }

  /**
   * Add purchased bandwidth addon.
   */
  public void addBandwidthAddon(long bytes) {
    if (bandwidthAddonBytes == null) {
      bandwidthAddonBytes = 0L;
    }
    bandwidthAddonBytes += bytes;
  }

}
