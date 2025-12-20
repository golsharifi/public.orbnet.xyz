package com.orbvpn.api.mapper;

import com.orbvpn.api.domain.dto.UserView;
import com.orbvpn.api.domain.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = { UserSubscriptionViewMapper.class })
public interface UserViewMapper {
  @Mapping(source = "uuid", target = "uuid")
  @Mapping(source = "role.name", target = "role")
  @Mapping(source = "reseller.id", target = "resellerId")
  @Mapping(source = "managedBy.id", target = "managedById")
  @Mapping(target = "managedBy", ignore = true) // Avoid circular reference, populate manually if needed
  @Mapping(target = "currentSubscription", ignore = true)
  @Mapping(target = "subscription", ignore = true)
  UserView toView(User user);
}
