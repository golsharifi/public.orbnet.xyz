package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;
import jakarta.validation.constraints.*;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenStakingConfigInput {
    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Base APY is required")
    @DecimalMin(value = "0.0", message = "Base APY must be at least 0")
    @DecimalMax(value = "1.0", message = "Base APY cannot exceed 1.0 (100%)")
    private BigDecimal baseApy;

    @NotNull(message = "Bonus APY per month is required")
    @DecimalMin(value = "0.0", message = "Bonus APY must be at least 0")
    @DecimalMax(value = "0.1", message = "Bonus APY cannot exceed 0.1 (10%)")
    private BigDecimal bonusApyPerMonth;

    @NotNull(message = "Minimum lock days is required")
    @Min(value = 1, message = "Minimum lock days must be at least 1")
    private Integer minimumLockDays;

    @NotNull(message = "Maximum lock days is required")
    @Min(value = 1, message = "Maximum lock days must be at least 1")
    private Integer maximumLockDays;

    @NotNull(message = "Minimum stake amount is required")
    @DecimalMin(value = "0.0", message = "Minimum stake amount must be at least 0")
    private BigDecimal minimumStakeAmount;

    private BigDecimal maximumStakeAmount;

    private Boolean isActive;

    private List<TokenStakingRequirementInput> requirements;
}