package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.ResellerLevelName;
import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class ResellerLevel {
  @Id
  private int id;

  @Column
  @Enumerated(EnumType.STRING)
  private ResellerLevelName name;

  @Column
  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100.0")
  private BigDecimal discountPercent;

  @Column
  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100.0")
  private BigDecimal minScore;
}
