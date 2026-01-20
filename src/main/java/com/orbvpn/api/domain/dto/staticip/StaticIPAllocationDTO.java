package com.orbvpn.api.domain.dto.staticip;

import com.orbvpn.api.domain.enums.StaticIPAllocationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaticIPAllocationDTO {
    private Long id;
    private String region;
    private String publicIp;
    private StaticIPAllocationStatus status;
    private int portForwardsIncluded;
    private int portForwardsUsed;
    private int portForwardsFromAddons;
    private LocalDateTime allocatedAt;
    private LocalDateTime configuredAt;
    private List<PortForwardRuleDTO> portForwardRules;
}
