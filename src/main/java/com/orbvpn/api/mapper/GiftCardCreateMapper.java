package com.orbvpn.api.mapper;

import com.orbvpn.api.domain.dto.GiftCardCreate;
import com.orbvpn.api.domain.entity.GiftCard;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GiftCardCreateMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "used", constant = "false")
    @Mapping(target = "redeemedBy", ignore = true)
    @Mapping(target = "redeemedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    GiftCard create(GiftCardCreate giftCardCreate);
}