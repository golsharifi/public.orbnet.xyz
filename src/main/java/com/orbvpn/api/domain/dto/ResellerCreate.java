package com.orbvpn.api.domain.dto;

import static com.orbvpn.api.domain.ValidationProperties.BAD_PASSWORD_MESSAGE;
import static com.orbvpn.api.domain.ValidationProperties.PASSWORD_PATTERN;

import com.orbvpn.api.domain.enums.ResellerLevelName;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResellerCreate {
  @Email
  private String email;

  @Pattern(regexp = PASSWORD_PATTERN, message = BAD_PASSWORD_MESSAGE)
  private String password;

  @DecimalMin(value = "0.0", inclusive = false)
  private BigDecimal credit;

  private ResellerLevelName level;

  private String phone;
}
