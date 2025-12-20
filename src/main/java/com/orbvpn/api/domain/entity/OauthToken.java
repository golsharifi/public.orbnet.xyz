package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.SocialMedia;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Entity
@EntityListeners(AuditingEntityListener.class)
public class OauthToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @Column
  private long exp;

  @Column
  private long iat;

  @Column(columnDefinition = "TEXT")
  private String token;

  @Column(columnDefinition = "TEXT")
  private String socialToken;

  @ManyToOne
  private User user;

  @Column
  @Enumerated(EnumType.STRING)
  private SocialMedia socialMedia;

  @Column
  @CreatedDate
  private LocalDateTime createdAt;

  @Column
  @LastModifiedDate
  private LocalDateTime updatedAt;
}
