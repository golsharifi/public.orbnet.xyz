package com.orbvpn.api.mapper;

import com.orbvpn.api.domain.dto.UserCreate;
import com.orbvpn.api.domain.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserCreateMapper {
  User createEntity(UserCreate userCreate);
}
