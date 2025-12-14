package com.orbvpn.api.domain.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for tracking reseller credit transactions.
 * Each record represents a credit addition or deduction.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "reseller_add_credit", indexes = {
    @Index(name = "idx_reseller_credit_reseller", columnList = "reseller_id"),
    @Index(name = "idx_reseller_credit_type", columnList = "transactionType"),
    @Index(name = "idx_reseller_credit_created", columnList = "createdAt")
})
@EntityListeners(AuditingEntityListener.class)
public class ResellerAddCredit {

  // Transaction type constants
  public static final String TYPE_ADMIN_CREDIT = "ADMIN_CREDIT";
  public static final String TYPE_ADMIN_DEBIT = "ADMIN_DEBIT";
  public static final String TYPE_USER_PURCHASE = "USER_PURCHASE";
  public static final String TYPE_DEVICE_PURCHASE = "DEVICE_PURCHASE";
  public static final String TYPE_REFUND = "REFUND";
  public static final String TYPE_ADJUSTMENT = "ADJUSTMENT";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  /**
   * Reference to the reseller profile.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reseller_id", nullable = false)
  private Reseller reseller;

  /**
   * Credit amount (positive for credit, negative for debit)
   */
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal credit;

  /**
   * Balance after this transaction
   */
  @Column(precision = 19, scale = 2)
  private BigDecimal balanceAfter;

  /**
   * Type of transaction
   */
  @Column(length = 30, nullable = false)
  private String transactionType;

  /**
   * Reason/description for this transaction
   */
  @Column(length = 500)
  private String reason;

  /**
   * Reference to related entity (e.g., user ID for purchase, payment ID)
   */
  @Column(length = 100)
  private String referenceId;

  /**
   * Reference type (USER, PAYMENT, ADJUSTMENT, etc.)
   */
  @Column(length = 30)
  private String referenceType;

  /**
   * Who performed this action (admin email or "SYSTEM")
   */
  @Column(length = 100)
  private String performedBy;

  @Column(nullable = false)
  @CreatedDate
  private LocalDateTime createdAt;

  /**
   * Legacy constructor for backward compatibility
   */
  public ResellerAddCredit(Reseller reseller, BigDecimal credit) {
    this.reseller = reseller;
    this.credit = credit;
    this.transactionType = credit.compareTo(BigDecimal.ZERO) >= 0 ? TYPE_ADMIN_CREDIT : TYPE_ADMIN_DEBIT;
  }

  /**
   * Create a credit (deposit) transaction
   */
  public static ResellerAddCredit createCredit(Reseller reseller, BigDecimal amount,
          BigDecimal balanceAfter, String reason, String performedBy) {
    return ResellerAddCredit.builder()
            .reseller(reseller)
            .credit(amount)
            .balanceAfter(balanceAfter)
            .transactionType(TYPE_ADMIN_CREDIT)
            .reason(reason)
            .performedBy(performedBy)
            .build();
  }

  /**
   * Create a debit (withdrawal) transaction
   */
  public static ResellerAddCredit createDebit(Reseller reseller, BigDecimal amount,
          BigDecimal balanceAfter, String reason, String performedBy) {
    return ResellerAddCredit.builder()
            .reseller(reseller)
            .credit(amount.negate())
            .balanceAfter(balanceAfter)
            .transactionType(TYPE_ADMIN_DEBIT)
            .reason(reason)
            .performedBy(performedBy)
            .build();
  }

  /**
   * Create a user purchase transaction (debit for buying user subscription)
   */
  public static ResellerAddCredit createUserPurchase(Reseller reseller, BigDecimal amount,
          BigDecimal balanceAfter, Integer userId, String userEmail) {
    return ResellerAddCredit.builder()
            .reseller(reseller)
            .credit(amount.negate())
            .balanceAfter(balanceAfter)
            .transactionType(TYPE_USER_PURCHASE)
            .reason("User subscription purchase: " + userEmail)
            .referenceId(String.valueOf(userId))
            .referenceType("USER")
            .performedBy("SYSTEM")
            .build();
  }

  /**
   * Create a device purchase transaction (debit for adding devices to user)
   */
  public static ResellerAddCredit createDevicePurchase(Reseller reseller, BigDecimal amount,
          BigDecimal balanceAfter, Integer userId, String userEmail, int deviceCount) {
    return ResellerAddCredit.builder()
            .reseller(reseller)
            .credit(amount.negate())
            .balanceAfter(balanceAfter)
            .transactionType(TYPE_DEVICE_PURCHASE)
            .reason(String.format("Device addon purchase: %d devices for %s", deviceCount, userEmail))
            .referenceId(String.valueOf(userId))
            .referenceType("USER_DEVICE")
            .performedBy("SYSTEM")
            .build();
  }

  /**
   * Check if this is a credit (positive) transaction
   */
  public boolean isCredit() {
    return credit != null && credit.compareTo(BigDecimal.ZERO) > 0;
  }

  /**
   * Check if this is a debit (negative) transaction
   */
  public boolean isDebit() {
    return credit != null && credit.compareTo(BigDecimal.ZERO) < 0;
  }
}
