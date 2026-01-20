package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BridgeConnectionStatsView {
    private Long totalConnections;
    private Long totalBytesSent;
    private Long totalBytesReceived;
    private Long averageSessionDurationSeconds;
}
