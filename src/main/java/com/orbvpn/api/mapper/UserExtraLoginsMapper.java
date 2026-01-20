// UserExtraLoginsMapper.java
package com.orbvpn.api.mapper;

import com.orbvpn.api.domain.dto.UserExtraLoginsView;
import com.orbvpn.api.domain.entity.UserExtraLogins;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserExtraLoginsMapper {
    @Mapping(target = "planName", source = "plan.name")
    @Mapping(target = "giftedByEmail", source = "giftedBy.email")
    UserExtraLoginsView toView(UserExtraLogins extraLogins);
}