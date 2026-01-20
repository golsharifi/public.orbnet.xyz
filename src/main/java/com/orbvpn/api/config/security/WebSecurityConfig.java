package com.orbvpn.api.config.security;

import com.orbvpn.api.component.CustomOAuth2LoginSuccessHandler;
import com.orbvpn.api.service.CustomDefaultOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
// securedEnabled = false because we use custom hierarchical @Secured handling in MethodSecurityConfig
@EnableMethodSecurity(securedEnabled = false, jsr250Enabled = true, prePostEnabled = true)
public class WebSecurityConfig {

  private final JwtTokenFilter jwtTokenFilter;
  private final OrbMeshApiKeyFilter orbmeshApiKeyFilter; // ✅ Add this
  private final CustomOAuth2LoginSuccessHandler customOAuth2LoginSuccessHandler;
  private final CustomDefaultOAuth2UserService oauthUserService;

  @Value("${application.website-url}")
  private String websiteUrl;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfig()))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/graphql/**", "/graphiql/**").permitAll()
            .anyRequest().permitAll())
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf
            .ignoringRequestMatchers("/graphql", "/graphql/**", "/graphiql", "/graphiql/**",
                "/api/email/**", "/email/**"))
        .headers(headers -> headers
            .contentSecurityPolicy(csp -> csp
                .policyDirectives(
                    "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://unpkg.com https://esm.sh https://cdn.jsdelivr.net; "
                        +
                        "style-src 'self' 'unsafe-inline' https://unpkg.com https://esm.sh https://cdn.jsdelivr.net; " +
                        "connect-src 'self' ws: wss: http: https:; " +
                        "img-src 'self' data: https:; " +
                        "font-src 'self' data: https:; " +
                        "worker-src 'self' blob:; " +
                        "child-src 'self' blob:")))
        .addFilterBefore(orbmeshApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class)
        .oauth2Login(oauth2 -> oauth2
            .userInfoEndpoint(userInfo -> userInfo
                .userService(oauthUserService))
            .successHandler(customOAuth2LoginSuccessHandler));

    return http.build();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
      throws Exception {
    return authenticationConfiguration.getAuthenticationManager();
  }

  private CorsConfigurationSource corsConfig() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(Arrays.asList("*"));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}