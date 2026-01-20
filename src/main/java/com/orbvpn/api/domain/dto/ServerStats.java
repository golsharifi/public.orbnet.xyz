package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ServerStats {
    private int totalServers;
    private int totalActiveServers;
    private List<ProtocolStats> serversByProtocol;
    private List<ContinentStats> serversByContinent;
    private int cryptoFriendlyCount;
}