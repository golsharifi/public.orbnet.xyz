package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DnsAdminWhitelistInput {
    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be positive")
    private Integer userId;

    @NotBlank(message = "IP address is required")
    @Pattern(
        regexp = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$",
        message = "Invalid IP address format"
    )
    private String ipAddress;

    private String label;

    private String deviceType;

    private Integer expiryDays;
}
