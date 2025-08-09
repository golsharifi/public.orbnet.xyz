package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.enums.ProtocolType;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class ProtocolStats {
    private ProtocolType protocol;
    private int count;
    private int activeCount;

}
