package com.orbvpn.api.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class OrbMeshServerInput {

    @NotBlank(message = "Server name is required")
    @Size(min = 3, max = 100)
    private String name;

    private String hostname;

    @NotBlank(message = "IP address is required")
    @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$|^[a-zA-Z0-9.-]+$", message = "Invalid IP address or hostname")
    private String ipAddress;

    @NotNull(message = "Port is required")
    @Min(value = 1, message = "Port must be between 1 and 65535")
    @Max(value = 65535, message = "Port must be between 1 and 65535")
    private Integer port;

    @NotBlank(message = "Location is required")
    private String location;

    @NotBlank(message = "Country code is required")
    @Size(min = 2, max = 2, message = "Country code must be 2 characters")
    private String country;

    @NotBlank(message = "Region is required")
    private String region;

    @NotEmpty(message = "At least one protocol is required")
    private List<String> protocols;

    @Min(value = 1, message = "Max connections must be positive")
    private Integer maxConnections = 1000;

    @Min(value = 1, message = "Bandwidth limit must be positive")
    private Integer bandwidthLimitMbps;

    private String publicKey;

    private String tlsCertificate;

    // Bridge support fields
    private Boolean bridgeCapable;

    private Integer bridgePriority;

    private Integer bridgeMaxSessions;
}