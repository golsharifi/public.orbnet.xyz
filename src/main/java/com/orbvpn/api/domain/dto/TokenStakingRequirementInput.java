package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;
import jakarta.validation.constraints.NotBlank;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenStakingRequirementInput {
    @NotBlank(message = "Requirement type is required")
    private String requirementType;

    @NotBlank(message = "Requirement value is required")
    private String requirementValue;
}