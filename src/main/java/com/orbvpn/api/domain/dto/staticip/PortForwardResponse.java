package com.orbvpn.api.domain.dto.staticip;

import com.orbvpn.api.domain.entity.PortForwardRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortForwardResponse {
    private boolean success;
    private String message;
    private PortForwardRule rule;
}
