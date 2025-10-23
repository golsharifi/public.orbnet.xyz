package com.orbvpn.api.mapper;

import com.orbvpn.api.domain.dto.UserView;
import com.orbvpn.api.domain.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = { UserSubscriptionViewMapper.class, ResellerViewMapper.class })
public interface UserViewMapper {
  @Mapping(source = "uuid", target = "uuid")
  @Mapping(source = "role.name", target = "role")
  @Mapping(source = "reseller.id", target = "resellerId")
  @Mapping(target = "currentSubscription", ignore = true)
  @Mapping(source = "currentSubscription", target = "subscription")
  UserView toView(User user);
}
