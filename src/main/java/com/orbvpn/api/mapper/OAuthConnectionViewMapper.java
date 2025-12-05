package com.orbvpn.api.mapper;

import com.orbvpn.api.domain.dto.passkey.OAuthConnectionView;
import com.orbvpn.api.domain.entity.OauthToken;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OAuthConnectionViewMapper {

    @Mapping(source = "socialMedia", target = "provider")
    @Mapping(target = "createdAt", expression = "java(oauthToken.getCreatedAt() != null ? oauthToken.getCreatedAt().toString() : null)")
    @Mapping(target = "updatedAt", expression = "java(oauthToken.getUpdatedAt() != null ? oauthToken.getUpdatedAt().toString() : null)")
    OAuthConnectionView toView(OauthToken oauthToken);
}
