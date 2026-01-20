package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.enums.DnsServiceType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DnsServiceRuleInput {
    @NotBlank(message = "Service ID is required")
    private String serviceId;

    @NotNull(message = "Service type is required")
    private DnsServiceType serviceType;

    private boolean enabled;

    private String preferredRegion;
}
