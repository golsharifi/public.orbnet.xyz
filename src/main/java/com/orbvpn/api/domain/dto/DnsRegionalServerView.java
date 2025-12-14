package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DnsRegionalServerView {
    private String code;
    private String name;
    private String location;
    private String ipv4;
    private String ipv6;
    private boolean healthy;
    private Integer latency;
    private int priority;
}
