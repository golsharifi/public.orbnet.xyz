package com.orbvpn.api.mapper;

import com.orbvpn.api.domain.dto.ExtraLoginsPlanView;
import com.orbvpn.api.domain.dto.ExtraLoginsPlanEdit;
import com.orbvpn.api.domain.entity.ExtraLoginsPlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ExtraLoginsPlanMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", constant = "true")
    ExtraLoginsPlan create(ExtraLoginsPlanEdit planEdit);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    void update(@MappingTarget ExtraLoginsPlan plan, ExtraLoginsPlanEdit planEdit);

    ExtraLoginsPlanView toView(ExtraLoginsPlan plan);
}