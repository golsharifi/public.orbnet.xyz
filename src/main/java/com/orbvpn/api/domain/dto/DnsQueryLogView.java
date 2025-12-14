package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DnsQueryLogView {
    private Long id;
    private Integer userId;
    private String domain;
    private String serviceId;
    private String region;
    private String responseType;
    private int latencyMs;
    private String timestamp;
}
