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
}
