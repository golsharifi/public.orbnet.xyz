package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import com.orbvpn.api.domain.enums.ProtocolType;

@Data
@Builder
public class MiningServerProtocolView {
    private Long id;
    private ProtocolType type;
    private Integer port;
    private Boolean enabled;
    private String configString;
    private String publicKey;
}