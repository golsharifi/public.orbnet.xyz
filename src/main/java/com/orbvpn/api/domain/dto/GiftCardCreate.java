package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiftCardCreate {
    @NotNull(message = "Group ID is required")
    private int groupId;

    @NotNull(message = "Validity days is required")
    @Positive(message = "Validity days must be positive")
    private int validityDays;
}