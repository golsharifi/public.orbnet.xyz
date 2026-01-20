package com.orbvpn.api.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "\"user\"")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User implements UserDetails {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @Column(unique = true, length = 36)
  private String uuid;

  @Column(nullable = false)
  private String username;

  @Column(nullable = false, unique = true)
  @Email
  private String email;

  @Column
  private String password;

  @Column
  private String oauthId;

  /**
   * Primary role for the user - this is the legacy single-role field.
   * Kept for backward compatibility with existing code and data.
   * Maps to the existing role_id column in the user table.
   */
  @ManyToOne
  @JoinColumn(name = "role_id")
  private Role role;

  @Column(nullable = false)
  private String radAccess = "not-a-regular-user";

  @Column(name = "country")
  private String country; // ISO country code (e.g., "IR", "US", "NL")

  @Column(name = "last_known_ip")
  private String lastKnownIp; // Last known IP address

  @Column(name = "ip_updated_at")
  private LocalDateTime ipUpdatedAt;

  @Column
  private String radAccessClear;

  @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
  private UserProfile profile;

  /**
   * The reseller profile for users with RESELLER role.
   * Only populated for users who are resellers.
   * Note: Uses Reseller type but conceptually this is the "reseller profile" data.
   */
  @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private Reseller resellerProfile;

  /**
   * The user (reseller) who manages/created this user.
   * For regular users, this points to the reseller who created their account.
   * Null for users who registered directly or for the owner reseller.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "managed_by_id")
  private User managedBy;

  /**
   * @deprecated Use managedBy instead. Kept for backward compatibility during migration.
   * This field maps to the legacy reseller_id column.
   */
  @Deprecated
  @ManyToOne
  @JoinColumn(name = "reseller_id")
  private Reseller reseller;

  @Column
  @CreatedDate
  private LocalDateTime createdAt;

  @Column
  @LastModifiedDate
  private LocalDateTime updatedAt;

  @Column(nullable = false)
  private boolean active = true;

  @Column
  private boolean enabled = true;

  @Column(columnDefinition = "BOOLEAN DEFAULT false")
  private boolean autoRenew = false;

  @Column(name = "aes_key", nullable = true)
  private String aesKey;

  @Column(name = "aes_iv", nullable = true)
  private String aesIv;

  @Column(name = "fcm_token")
  private String fcmToken;

  @Transient
  private UserSubscription subscription;

  @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE)
  private List<PasswordReset> passwordResetList;

  @OneToMany(mappedBy = "creator", cascade = CascadeType.REMOVE)
  private List<Ticket> ticketList;

  @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY)
  private List<UserSubscription> userSubscriptionList;

  @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE)
  private List<Payment> paymentList;

  @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private ReferralCode referralCode;

  /**
   * The user who referred this user (for MLM tracking).
   * Null if user registered without a referral code.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "referred_by_id")
  private User referredBy;

  // Stripe related fields and relationships
  @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private StripeCustomer stripeCustomer;

  @Column(name = "stripe_customer_id")
  private String stripeCustomerId;

  @Column(name = "wallet_address")
  private String walletAddress;

  // Helper methods for Stripe customer management
  public String getEffectiveStripeCustomerId() {
    if (stripeCustomerId != null) {
      return stripeCustomerId;
    }
    return stripeCustomer != null ? stripeCustomer.getStripeId() : null;
  }

  public void setStripeData(StripeCustomer stripeCustomer) {
    this.stripeCustomer = stripeCustomer;
    this.stripeCustomerId = stripeCustomer != null ? stripeCustomer.getStripeId() : null;
  }

  // UserDetails implementation methods
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    if (role != null) {
      return Collections.singleton(role.getName());
    }
    return Collections.emptySet();
  }

  /**
   * Check if user has a specific role.
   */
  public boolean hasRole(com.orbvpn.api.domain.enums.RoleName roleName) {
    return role != null && role.getName() == roleName;
  }

  /**
   * Check if this user is a reseller.
   */
  public boolean isReseller() {
    return hasRole(com.orbvpn.api.domain.enums.RoleName.RESELLER);
  }

  /**
   * Check if this user is an admin.
   */
  public boolean isAdmin() {
    return hasRole(com.orbvpn.api.domain.enums.RoleName.ADMIN);
  }

  @Override
  public boolean isAccountNonExpired() {
    return enabled;
  }

  @Override
  public boolean isAccountNonLocked() {
    return enabled;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return enabled;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getLastKnownIp() {
    return lastKnownIp;
  }

  public void setLastKnownIp(String lastKnownIp) {
    this.lastKnownIp = lastKnownIp;
  }

  public LocalDateTime getIpUpdatedAt() {
    return ipUpdatedAt;
  }

  public void setIpUpdatedAt(LocalDateTime ipUpdatedAt) {
    this.ipUpdatedAt = ipUpdatedAt;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  // Stripe specific getters and setters with proper null handling
  public StripeCustomer getStripeCustomer() {
    return stripeCustomer;
  }

  public void setStripeCustomer(StripeCustomer stripeCustomer) {
    this.stripeCustomer = stripeCustomer;
    if (stripeCustomer != null) {
      this.stripeCustomerId = stripeCustomer.getStripeId();
    }
  }

  public String getStripeCustomerId() {
    return stripeCustomerId != null ? stripeCustomerId : (stripeCustomer != null ? stripeCustomer.getStripeId() : null);
  }

  public void setStripeCustomerId(String stripeCustomerId) {
    this.stripeCustomerId = stripeCustomerId;
  }

  public String getFcmToken() {
    return fcmToken;
  }

  public void setFcmToken(String fcmToken) {
    this.fcmToken = fcmToken;
  }

  public int getMultiLoginCount() {
    UserSubscription currentSubscription = getCurrentSubscription();
    return currentSubscription != null ? currentSubscription.getMultiLoginCount() : 0;
  }

  public UserSubscription getCurrentSubscription() {
    // First check if we already have the subscription cached in the transient field
    if (this.subscription != null) {
      return this.subscription;
    }

    // Check if userSubscriptionList is initialized to avoid lazy loading exception
    if (userSubscriptionList != null &&
        org.hibernate.Hibernate.isInitialized(userSubscriptionList) &&
        !userSubscriptionList.isEmpty()) {

      LocalDateTime now = LocalDateTime.now();
      UserSubscription current = userSubscriptionList.stream()
          .filter(sub -> sub.getExpiresAt() != null && sub.getExpiresAt().isAfter(now))
          .findFirst()
          .orElse(null);
      this.subscription = current; // Cache the result
      return current;
    }

    // If userSubscriptionList is not initialized or is empty, return null
    // This avoids the lazy initialization exception
    return null;
  }

  public String getWalletAddress() {
    return walletAddress;
  }

}