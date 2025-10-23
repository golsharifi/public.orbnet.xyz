package com.orbvpn.api.mapper;

import com.orbvpn.api.domain.dto.GiftCardView;
import com.orbvpn.api.domain.entity.GiftCard;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GiftCardViewMapper {
    @Mapping(source = "group.id", target = "groupId")
    @Mapping(source = "group.name", target = "groupName")
    @Mapping(source = "redeemedBy.email", target = "redeemedByEmail")
    @Mapping(source = "cancelledBy.email", target = "cancelledByEmail")
    GiftCardView toView(GiftCard giftCard);
}