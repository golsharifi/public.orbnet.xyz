package com.orbvpn.api.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input for completing an ad viewing session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteAdSessionInput {

    @NotBlank(message = "Session ID is required")
    private String sessionId;

    @NotBlank(message = "Signature is required")
    private String signature;

    @Min(value = 1, message = "Duration must be at least 1 second")
    private int durationSeconds;
}
