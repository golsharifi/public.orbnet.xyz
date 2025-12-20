package com.orbvpn.api.service;

import com.orbvpn.api.domain.enums.SocialMedia;
import com.orbvpn.api.exception.OauthLoginException;
import com.orbvpn.api.service.social_login.OauthService;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CustomDefaultOAuth2UserService extends DefaultOAuth2UserService {
    private final OauthService oauthService;

    public CustomDefaultOAuth2UserService(@Lazy OauthService oauthService) {
        this.oauthService = oauthService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        System.out.println("CustomOAuth2UserService invoked");
        String provider = userRequest.getClientRegistration().getRegistrationId();
        switch (provider) {
            case "facebook":
                String tokenFacebook = userRequest.getAccessToken().getTokenValue();
                this.oauthService.oauthLogin(tokenFacebook, SocialMedia.FACEBOOK);
                break;
            case "amazon":
                String tokenAmazon = userRequest.getAccessToken().getTokenValue();
                this.oauthService.oauthLogin(tokenAmazon, SocialMedia.AMAZON);
                break;
            case "github":
                String tokenGithub = userRequest.getAccessToken().getTokenValue();
                this.oauthService.oauthLogin(tokenGithub, SocialMedia.GITHUB);
                break;
            default:
                throw new OauthLoginException("Unknown provider.");
        }
        OAuth2User user = super.loadUser(userRequest);
        // Kiểm tra hoặc chỉnh sửa nonce tại đây nếu cần
        return user;
    }
}
