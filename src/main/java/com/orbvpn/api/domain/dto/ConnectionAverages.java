package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConnectionAverages {
    private Float averageUploadSpeed;
    private Float averageDownloadSpeed;
    private Float averageNetworkSpeed;
    private Integer averageResponseTime;
    private Integer averageLatency;
}