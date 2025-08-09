package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiftCardRedemption {
    @NotBlank(message = "Gift card code is required")
    private String code;
}