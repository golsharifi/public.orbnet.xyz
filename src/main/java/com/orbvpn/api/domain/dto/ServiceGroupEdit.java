package com.orbvpn.api.domain.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceGroupEdit {

  @NotBlank
  private String name;
  @NotBlank
  private String description;

  @NotNull
  private Locale language;

  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100", inclusive = false)
  private BigDecimal discount = BigDecimal.ZERO;

  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100", inclusive = false)
  private BigDecimal discount3 = BigDecimal.ZERO;

  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100", inclusive = false)
  private BigDecimal discount6 = BigDecimal.ZERO;

  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100", inclusive = false)
  private BigDecimal discount12 = BigDecimal.ZERO;

  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100", inclusive = false)
  private BigDecimal discount24 = BigDecimal.ZERO;

  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100", inclusive = false)
  private BigDecimal discount36 = BigDecimal.ZERO;

  @DecimalMin(value = "0.0")
  @DecimalMax(value = "100", inclusive = false)
  private BigDecimal discountLifetime = BigDecimal.ZERO;

  @NotEmpty
  private List<Integer> gateways;
  private List<Integer> allowedGeolocations = new ArrayList<>();
  private List<Integer> disAllowedGeolocations = new ArrayList<>();
}
