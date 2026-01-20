package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
public class BridgeConnectionView {
    private Long bridgeServerId;
    private Long exitServerId;
    private String protocol;
    private String status;
    private OffsetDateTime connectedAt;
    private OffsetDateTime disconnectedAt;
    private Long bytesSent;
    private Long bytesReceived;
    private Integer sessionDurationSeconds;
}
