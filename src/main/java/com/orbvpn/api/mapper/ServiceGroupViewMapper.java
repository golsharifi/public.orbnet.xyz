package com.orbvpn.api.mapper;

import com.orbvpn.api.domain.dto.ServiceGroupView;
import com.orbvpn.api.domain.entity.ServiceGroup;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = {
    GatewayViewMapper.class, GeolocationViewMapper.class, GroupViewMapper.class })
public interface ServiceGroupViewMapper {
  ServiceGroupView toView(ServiceGroup serviceGroup);
}