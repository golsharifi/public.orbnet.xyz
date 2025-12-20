package com.orbvpn.api.config.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.orbvpn.api.properties.JWTProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequestEntityConverter;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
  private final JWTProperties jwtProperties;

  @Bean
  public Algorithm jwtAlgorithm() {
    return Algorithm.HMAC256(jwtProperties.getSecret());
  }

  @Bean
  public JWTVerifier verifier(Algorithm algorithm) {
    return JWT
        .require(algorithm)
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  /**
   * Configures role hierarchy for RBAC.
   * ADMIN > RESELLER > USER means:
   * - ADMIN has all permissions of RESELLER and USER
   * - RESELLER has all permissions of USER
   * - USER has base permissions
   *
   * This allows @Secured("USER") to also accept RESELLER and ADMIN roles.
   * Note: We use fromHierarchy() instead of withDefaultRolePrefix() because
   * our roles don't have the ROLE_ prefix (they're stored as USER, ADMIN, RESELLER).
   */
  @Bean
  public RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy("""
        ADMIN > RESELLER
        RESELLER > USER
        """);
  }

  @SuppressWarnings("removal")
  @Bean
  public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient(
      OAuth2AuthorizationCodeGrantRequestEntityConverter oAuth2AuthorizationCodeGrantRequestEntityConverter) {
    var client = new DefaultAuthorizationCodeTokenResponseClient();
    client.setRequestEntityConverter(oAuth2AuthorizationCodeGrantRequestEntityConverter);
    return client;
  }

  @Bean
  public OidcAuthorizationCodeAuthenticationProvider auth2AuthorizationCodeAuthenticationProvider(
      OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient) {
    return new OidcAuthorizationCodeAuthenticationProvider(accessTokenResponseClient, new OidcUserService());
  }

  @Bean
  public HttpFirewall allowSemicolonHttpFirewall() {
    StrictHttpFirewall firewall = new StrictHttpFirewall();
    firewall.setAllowSemicolon(true); // Allow semicolons in URLs
    return firewall;
  }

}
