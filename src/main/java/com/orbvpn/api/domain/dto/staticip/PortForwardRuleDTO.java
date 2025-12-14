package com.orbvpn.api.domain.dto.staticip;

import com.orbvpn.api.domain.enums.PortForwardProtocol;
import com.orbvpn.api.domain.enums.PortForwardStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortForwardRuleDTO {
    private Long id;
    private Integer externalPort;
    private Integer internalPort;
    private PortForwardProtocol protocol;
    private PortForwardStatus status;
    private String description;
    private boolean enabled;
    private boolean isFromAddon;
    private LocalDateTime createdAt;
}
