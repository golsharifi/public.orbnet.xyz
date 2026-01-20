// src/main/java/com/orbvpn/api/domain/dto/OrbMeshUsageInput.java
package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrbMeshUsageInput {
    private Integer userId;
    private Long serverId;
    private String sessionId;
    private Long bytesSent;
    private Long bytesReceived;
    private Integer durationSeconds;
    private String protocol; // "teams","google", "shaparak", "doh", "https", "yandex", "wechat"
    private LocalDateTime disconnectedAt;
}