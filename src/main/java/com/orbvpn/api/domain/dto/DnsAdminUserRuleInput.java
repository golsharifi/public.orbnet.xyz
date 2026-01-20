package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.enums.DnsServiceType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DnsAdminUserRuleInput {
    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be positive")
    private Integer userId;

    @NotBlank(message = "Service ID is required")
    private String serviceId;

    @NotNull(message = "Service type is required")
    private DnsServiceType serviceType;

    private boolean enabled;

    private String preferredRegion;
}
