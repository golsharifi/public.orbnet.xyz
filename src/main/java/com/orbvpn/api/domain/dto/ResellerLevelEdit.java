package com.orbvpn.api.domain.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResellerLevelEdit {

  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100.0")
  private BigDecimal discountPercent;

  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100.0")
  private BigDecimal minScore;

}
