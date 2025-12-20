package com.orbvpn.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class DynamicOAuthConfig {

    @Value("${application.website-url:https://orbnet.xyz}")
    private String defaultWebsiteUrl;

    @Bean
    @RequestScope
    public OAuthRedirectResolver oAuthRedirectResolver(HttpServletRequest request) {
        return new OAuthRedirectResolver(request, defaultWebsiteUrl);
    }

    public static class OAuthRedirectResolver {
        private final String baseUrl;

        public OAuthRedirectResolver(HttpServletRequest request, String defaultUrl) {
            // Get from X-Forwarded headers or direct request
            String proto = request.getHeader("X-Forwarded-Proto");
            String host = request.getHeader("X-Forwarded-Host");

            if (proto != null && host != null) {
                this.baseUrl = proto + "://" + host;
                log.debug("Using forwarded URL: {}", this.baseUrl);
            } else {
                String scheme = request.getScheme();
                String serverName = request.getServerName();
                int serverPort = request.getServerPort();

                if (serverName != null && !serverName.isEmpty()) {
                    StringBuilder url = new StringBuilder();
                    url.append(scheme).append("://").append(serverName);

                    // Only add port if non-standard
                    if ((serverPort != 80 && "http".equals(scheme)) ||
                            (serverPort != 443 && "https".equals(scheme))) {
                        url.append(":").append(serverPort);
                    }

                    this.baseUrl = url.toString();
                    log.debug("Using request URL: {}", this.baseUrl);
                } else {
                    this.baseUrl = defaultUrl;
                    log.debug("Using default URL: {}", this.baseUrl);
                }
            }
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getRedirectUrl(String provider) {
            return baseUrl + "/login/oauth2/code/" + provider;
        }
    }
}