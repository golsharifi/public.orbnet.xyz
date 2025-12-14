package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.enums.AlertType;
import com.orbvpn.api.domain.enums.AlertSeverity;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class AlertMessage {
    private String id;
    private AlertType type;
    private String serverId;
    private String serverName;
    private String message;
    private AlertSeverity severity;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;
}