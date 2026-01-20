package com.orbvpn.api.domain.dto;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class NewsEdit {

  @NotBlank
  private String title;

  @NotBlank
  private String description;

  private boolean notifyByEmail;

  @NotEmpty
  private List<Integer> roles;
}
