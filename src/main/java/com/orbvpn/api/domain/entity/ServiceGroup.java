package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.entity.converter.LocaleConverter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE service_group SET deleted = true WHERE id=?")
@SQLRestriction("deleted=false")
public class ServiceGroup {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String description;

  @Column
  @Convert(converter = LocaleConverter.class)
  private Locale language;

  @Column
  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100", inclusive = false)
  private BigDecimal discount;

  @Column(name = "discount_3")
  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100", inclusive = false)
  private BigDecimal discount3;

  @Column(name = "discount_6")
  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100", inclusive = false)
  private BigDecimal discount6;

  @Column(name = "discount_12")
  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100", inclusive = false)
  private BigDecimal discount12;

  @Column(name = "discount_24")
  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100", inclusive = false)
  private BigDecimal discount24;

  @Column(name = "discount_36")
  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100", inclusive = false)
  private BigDecimal discount36;

  @Column
  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100", inclusive = false)
  private BigDecimal discountLifetime;

  @ManyToMany(fetch = FetchType.LAZY)
  private List<Gateway> gateways;

  @ManyToMany
  private List<Geolocation> allowedGeolocations;

  @ManyToMany
  private List<Geolocation> disAllowedGeolocations;

  @OneToMany(mappedBy = "serviceGroup", cascade = CascadeType.ALL)
  private List<Group> groups;

  @Column
  private boolean deleted;

  @Column
  @CreatedDate
  private LocalDateTime createdAt;

  @Column
  @LastModifiedDate
  private LocalDateTime updatedAt;
}
