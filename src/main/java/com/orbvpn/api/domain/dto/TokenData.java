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
}
