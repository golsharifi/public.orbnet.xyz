package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResellerUserEdit {
  private String password;
  private Integer resellerId;
  private Integer groupId;
  private Integer multiLoginCount;
  private UserProfileEdit userProfileEdit;
}