package com.orbvpn.api.domain.dto.passkey;

import com.orbvpn.api.domain.enums.SocialMedia;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OAuthConnectionView {
    private Integer id;
    private SocialMedia provider;
    private String createdAt;
    private String updatedAt;
}
