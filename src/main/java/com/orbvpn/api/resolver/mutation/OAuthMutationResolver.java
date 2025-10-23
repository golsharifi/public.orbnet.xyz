package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.config.security.Unsecured;
import com.orbvpn.api.domain.dto.AuthenticatedUser;
import com.orbvpn.api.domain.enums.SocialMedia;
import com.orbvpn.api.service.social_login.OauthService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class OAuthMutationResolver {

  private final OauthService oauthService;

  @MutationMapping
  @Unsecured
  public AuthenticatedUser oauthLogin(
          @Argument @NotBlank(message = "Token is required") String token,
          @Argument @NotNull(message = "Social media platform is required") SocialMedia socialMedia) {
    log.info("Processing OAuth login for platform: {}", socialMedia);
    try {
      return oauthService.oauthLogin(token, socialMedia);
    } catch (Exception e) {
      log.error("Error processing OAuth login - Error: {}", e.getMessage(), e);
      throw e;
    }
  }
}