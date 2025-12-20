package com.orbvpn.api.domain.dto.staticip;

import com.orbvpn.api.domain.enums.PortForwardProtocol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePortForwardInput {
    private Long allocationId;
    private Integer externalPort;
    private Integer internalPort;
    private PortForwardProtocol protocol;
    private String description;
}
