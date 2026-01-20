package com.orbvpn.api.repository.projections;

import com.orbvpn.api.domain.enums.ProtocolType;

public interface ProtocolStatsProjection {
    ProtocolType getProtocol();

    Long getServerCount();

    Long getActiveCount();

}
