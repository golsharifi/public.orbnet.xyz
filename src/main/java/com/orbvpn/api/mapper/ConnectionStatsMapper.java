package com.orbvpn.api.mapper;

import com.orbvpn.api.domain.dto.ConnectionStatsView;
import com.orbvpn.api.domain.entity.ConnectionStats;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ConnectionStatsMapper {
    @Mapping(source = "server.id", target = "serverId")
    @Mapping(source = "server.hostName", target = "serverName")
    ConnectionStatsView toView(ConnectionStats stats);
}