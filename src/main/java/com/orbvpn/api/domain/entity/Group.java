package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.IpType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "group_app")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE group_app SET deleted = true WHERE id=?")
@SQLRestriction("deleted=false")
public class Group {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @ManyToOne(fetch = FetchType.LAZY)
  private ServiceGroup serviceGroup;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String description;

  @Column(nullable = false)
  private String tagName;

  @Column
  @PositiveOrZero
  private int duration;

  @Column
  @DecimalMin(value = "0.0", inclusive = false)
  private BigDecimal price;

  @Column
  private String usernamePostfix;

  @Column
  private String usernamePostfixId;

  @Column
  @DecimalMin(value = "0")
  private BigInteger dailyBandwidth;

  @Column
  private int multiLoginCount;

  @Column
  @DecimalMin(value = "0")
  private BigInteger downloadUpload;

  @Enumerated(EnumType.STRING)
  private IpType ip;

  @Column
  private boolean registrationGroup;

  @Column
  private boolean deleted;

  @Column
  @CreatedDate
  private LocalDateTime createdAt;

  @Column
  @LastModifiedDate
  private LocalDateTime updatedAt;

  @Column(name = "min_daily_ads")
  private Integer minDailyAds;

  @Column(name = "min_weekly_ads")
  private Integer minWeeklyAds;

  public Integer getMinWeeklyAds() {
    return minWeeklyAds;
  }

  public void setMinWeeklyAds(Integer minWeeklyAds) {
    this.minWeeklyAds = minWeeklyAds;
  }

  public Integer getMinDailyAds() {
    return minDailyAds;
  }

  public void setMinDailyAds(Integer minDailyAds) {
    this.minDailyAds = minDailyAds;
  }

}
