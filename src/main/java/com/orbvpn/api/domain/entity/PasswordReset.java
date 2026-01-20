package com.orbvpn.api.domain.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class PasswordReset {
  @Id
  private String token;

  @ManyToOne
  private User user;

  @Column
  @CreatedDate
  private LocalDateTime createdAt;

  @Column
  private LocalDateTime expiresAt;

  /**
   * Check if this password reset token has expired.
   * Tokens without expiration (legacy) are considered expired for safety.
   */
  public boolean isExpired() {
    // Legacy tokens without expiration are considered expired
    if (expiresAt == null) {
      return true;
    }
    return LocalDateTime.now().isAfter(expiresAt);
  }

  /**
   * Check if this token is valid (not expired)
   */
  public boolean isValid() {
    return !isExpired();
  }
}
