package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class BridgeServerView {
    private Long id;
    private String name;
    private String location;
    private String country;
    private String countryCode;
    private String ipAddress;
    private Integer port;
    private List<String> protocols;
    private Boolean online;
    private Float load;
    private Integer latencyMs;
    private Integer priority;
    private Integer maxSessions;
}
