package com.orbvpn.api.domain.dto;

import static com.orbvpn.api.domain.ValidationProperties.BAD_PASSWORD_MESSAGE;
import static com.orbvpn.api.domain.ValidationProperties.PASSWORD_PATTERN;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import java.util.Locale;

@Getter
@Setter
public class ResellerUserCreate {
  @Email
  private String email;

  @Pattern(regexp = PASSWORD_PATTERN, message = BAD_PASSWORD_MESSAGE, flags = Pattern.Flag.CASE_INSENSITIVE)
  private String password = null; // Default empty string to skip validation when not provided

  @Positive
  private int groupId;

  private String firstName;
  private String lastName;
  private String userName;
  private String address;
  private String city;
  private String country;
  private String postalCode;
  private String phone;
  private String birthDate;
  private Locale language;
  private String telegramUsername;
  private String telegramChatId;
  private int login;

  // Custom setter for password
  public void setPassword(String password) {
    if (password != null && password.isEmpty()) {
      this.password = null; // Treat empty strings as null
    } else {
      this.password = password;
    }
  }

}
