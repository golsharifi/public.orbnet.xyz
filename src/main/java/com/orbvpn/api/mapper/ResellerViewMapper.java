package com.orbvpn.api.mapper;

import com.orbvpn.api.domain.dto.ResellerView;
import com.orbvpn.api.domain.entity.Reseller;
import com.orbvpn.api.domain.entity.ResellerLevel;
import com.orbvpn.api.domain.enums.ResellerLevelName;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(componentModel = "spring", uses = { ServiceGroupViewMapper.class, UserViewMapper.class })
public interface ResellerViewMapper {
  @Mappings({
    @Mapping(source = "user", target = "user"),
    @Mapping(source = "user.id", target = "userId"),
    @Mapping(source = "user.email", target = "email"),
    @Mapping(source = "user.profile.firstName", target = "firstName"),
    @Mapping(source = "user.profile.lastName", target = "lastName"),
    @Mapping(expression = "java(reseller.getEffectivePhone())", target = "phone"),
  })
  ResellerView toView(Reseller reseller);

  default ResellerLevelName mapResellerLeveLName(ResellerLevel resellerLevel) {
    return resellerLevel.getName();
  }
}
