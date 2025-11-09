package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ServersByProtocol {
    private List<MiningServerView> vlessServers;
    private List<MiningServerView> realityServers;
    private List<MiningServerView> wireguardServers;
    private List<MiningServerView> openconnectServers;
}