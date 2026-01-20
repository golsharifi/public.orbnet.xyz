package com.orbvpn.api.domain.dto.staticip;

import com.orbvpn.api.domain.enums.PortForwardProtocol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePortForwardRequest {

    @NotNull(message = "Allocation ID is required")
    private Long allocationId;

    @NotNull(message = "External port is required")
    @Min(value = 1024, message = "External port must be >= 1024")
    @Max(value = 65535, message = "External port must be <= 65535")
    private Integer externalPort;

    @NotNull(message = "Internal port is required")
    @Min(value = 1, message = "Internal port must be >= 1")
    @Max(value = 65535, message = "Internal port must be <= 65535")
    private Integer internalPort;

    @NotNull(message = "Protocol is required")
    private PortForwardProtocol protocol;

    private String description;
}
