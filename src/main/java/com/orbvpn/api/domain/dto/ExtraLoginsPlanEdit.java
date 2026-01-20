package com.orbvpn.api.domain.dto;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

@Data
public class ExtraLoginsPlanEdit {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Login count is required")
    @Min(value = 1, message = "Login count must be at least 1")
    private int loginCount;

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.01", message = "Base price must be greater than 0")
    private BigDecimal basePrice;

    @NotNull(message = "Duration days is required")
    @Min(value = 0, message = "Duration days must be 0 or greater")
    private int durationDays;

    private boolean subscription;
    private boolean giftable;
    private String mobileProductId;

    @DecimalMin(value = "0.0", message = "Bulk discount percentage must be 0 or greater")
    private BigDecimal bulkDiscountPercent;

    @Min(value = 1, message = "Minimum quantity must be at least 1")
    private int minimumQuantity;
}