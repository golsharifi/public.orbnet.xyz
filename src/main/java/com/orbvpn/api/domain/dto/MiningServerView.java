package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class MiningServerView {
    private Long id;
    private Integer operatorId;
    private String operatorEmail;
    private String hostName;
    private String publicIp;
    private String location;
    private String city;
    private String country;
    private String continent;
    private Boolean cryptoFriendly;
    private Boolean isMiningEnabled;
    private Integer activeConnections;
    private List<MiningServerProtocolView> protocols;
    private ServerMetrics metrics;
}