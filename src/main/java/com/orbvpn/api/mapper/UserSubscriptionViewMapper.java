package com.orbvpn.api.mapper;

import com.orbvpn.api.domain.dto.UserSubscriptionView;
import com.orbvpn.api.domain.entity.UserSubscription;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = GroupViewMapper.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserSubscriptionViewMapper {
  UserSubscriptionView toView(UserSubscription userSubscription);
}
