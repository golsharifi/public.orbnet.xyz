package com.orbvpn.api.domain.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reseller entity - represents a reseller's business profile.
 *
 * Architecture Notes:
 * - A Reseller has an associated User account (with RESELLER role) for authentication
 * - Regular Users are managed by a Reseller (user.managedBy points to the reseller's User)
 * - The User entity also has a reference back to this entity via user.resellerProfile
 *
 * Migration Path:
 * - Legacy: user.reseller pointed to this entity directly
 * - New: user.managedBy points to the reseller's User account (User→User relationship)
 * - Both are maintained for backward compatibility during migration
 *
 * @see ResellerProfile - Alias for this entity (same class)
 * @see User#managedBy - New relationship (User→User)
 * @see User#reseller - Legacy relationship (deprecated)
 */
@Entity
@Table(name = "reseller")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class Reseller {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  /**
   * The user account associated with this reseller profile.
   * This user must have the RESELLER role.
   */
  @OneToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "user_id")
  private User user;

  @Column
  private BigDecimal credit;

  @ManyToOne(fetch = FetchType.LAZY)
  private ResellerLevel level;

  @Column
  private LocalDateTime levelSetDate;

  /**
   * Tracks consecutive checks where reseller scored below their current level.
   * Demotion only occurs after this reaches the grace threshold.
   * Reset to 0 on promotion or when score improves above current level threshold.
   */
  @Column
  private Integer demotionGraceCount = 0;

  /**
   * @deprecated Use UserProfile.phone instead for the reseller's phone number.
   * This field is kept for backward compatibility.
   */
  @Deprecated
  @Column
  private String phone;

  @Column
  private boolean enabled = true;

  @ManyToMany
  private Set<ServiceGroup> serviceGroups = new HashSet<>();

  @Column
  @CreatedDate
  private LocalDateTime createdAt;

  @Column
  @LastModifiedDate
  private LocalDateTime updatedAt;

  @OneToMany(mappedBy = "reseller", cascade = CascadeType.REMOVE)
  private List<ResellerAddCredit> resellerAddCreditList;

  /**
   * Helper method to get phone from user's profile (preferred) or legacy phone field.
   */
  public String getEffectivePhone() {
    if (user != null && user.getProfile() != null && user.getProfile().getPhone() != null) {
      return user.getProfile().getPhone();
    }
    return phone; // Fallback to legacy field
  }
}
