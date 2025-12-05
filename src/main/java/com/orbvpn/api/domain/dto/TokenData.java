package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TokenData {
  private String email;
  private String tokenValue;
  private String oauthId;
  private long exp;
  private long iat;

  // OAuth profile information
  private String firstName;
  private String lastName;
  private String fullName;
  private String pictureUrl;

  public String getEmail() {
    return this.email;
  }

  public String getTokenValue() {
    return this.tokenValue;
  }

  public String getOauthId() {
    return this.oauthId;
  }

  public long getExp() {
    return this.exp;
  }

  public long getIat() {
    return this.iat;
  }

  /**
   * Get first name from OAuth data, parsing from fullName if necessary
   */
  public String getFirstName() {
    if (this.firstName != null) {
      return this.firstName;
    }
    if (this.fullName != null && !this.fullName.isEmpty()) {
      String[] parts = this.fullName.split(" ", 2);
      return parts[0];
    }
    return null;
  }

  /**
   * Get last name from OAuth data, parsing from fullName if necessary
   */
  public String getLastName() {
    if (this.lastName != null) {
      return this.lastName;
    }
    if (this.fullName != null && !this.fullName.isEmpty()) {
      String[] parts = this.fullName.split(" ", 2);
      return parts.length > 1 ? parts[1] : null;
    }
    return null;
  }
}
