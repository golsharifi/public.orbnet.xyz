package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.enums.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TicketCreate {
  @NotBlank
  private String subject;
  @NotBlank
  private String text;
  @NotNull
  private TicketCategory category;
}
