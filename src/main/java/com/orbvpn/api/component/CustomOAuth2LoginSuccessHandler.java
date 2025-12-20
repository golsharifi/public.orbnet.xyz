package com.orbvpn.api.component;

import com.orbvpn.api.domain.dto.AuthenticatedUser;
import com.orbvpn.api.domain.enums.SocialMedia;
import com.orbvpn.api.exception.OauthLoginException;
import com.orbvpn.api.service.social_login.OauthService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class CustomOAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private OauthService oauthService;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    private AuthenticatedUser authenticatedUser;

    // Use default constructor instead of @Lazy constructor injection
    public CustomOAuth2LoginSuccessHandler() {
        // Default constructor
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication)
            throws IOException {

        String code = request.getParameter("code");

        OAuth2AuthenticationToken oAuth2AuthenticationToken = (OAuth2AuthenticationToken) authentication;
        String provider = oAuth2AuthenticationToken.getAuthorizedClientRegistrationId();
        String principal = oAuth2AuthenticationToken.getPrincipal().getName();
        System.out.println(String.format("====>Received authorization code: %s from %s", code, provider));

        switch (provider) {
            case "google":
                DefaultOidcUser oauthUserGoogle = (DefaultOidcUser) authentication.getPrincipal();
                String tokenGoogle = oauthUserGoogle.getIdToken().getTokenValue();
                authenticatedUser = this.oauthService.oauthLogin(tokenGoogle, SocialMedia.GOOGLE);
                break;
            case "apple":
                DefaultOidcUser oauthUserApple = (DefaultOidcUser) authentication.getPrincipal();
                String tokenApple = oauthUserApple.getIdToken().getTokenValue();
                authenticatedUser = this.oauthService.oauthLogin(tokenApple, SocialMedia.APPLE);
                break;
            case "facebook":
                String tokenFacebook = authorizedClientService.loadAuthorizedClient(provider, principal)
                        .getAccessToken().getTokenValue();
                authenticatedUser = this.oauthService.oauthLogin(tokenFacebook, SocialMedia.FACEBOOK);
                break;
            case "linkedin":
                DefaultOidcUser oauthUserLinkedIn = (DefaultOidcUser) authentication.getPrincipal();
                String tokenLinkedIn = oauthUserLinkedIn.getIdToken().getTokenValue();
                authenticatedUser = this.oauthService.oauthLogin(tokenLinkedIn, SocialMedia.LINKEDIN);
                break;
            case "twitter":
                DefaultOidcUser oauthUserTwitter = (DefaultOidcUser) authentication.getPrincipal();
                String tokenTwitter = oauthUserTwitter.getIdToken().getTokenValue();
                authenticatedUser = this.oauthService.oauthLogin(tokenTwitter, SocialMedia.TWITTER);
                break;
            case "amazon":
                String tokenAmazon = authorizedClientService.loadAuthorizedClient(provider, principal).getAccessToken()
                        .getTokenValue();
                authenticatedUser = this.oauthService.oauthLogin(tokenAmazon, SocialMedia.AMAZON);
                break;
            case "github":
                String tokenGithub = authorizedClientService.loadAuthorizedClient(provider, principal).getAccessToken()
                        .getTokenValue();
                authenticatedUser = this.oauthService.oauthLogin(tokenGithub, SocialMedia.GITHUB);
                break;
            default:
                throw new OauthLoginException("Unknown provider.");
        }

        // Check if the request is from a mobile app
        if (isMobile(request)) {
            System.out.println("Request is from a mobile device.");
            // For mobile app, use HTTP 302 redirect to custom scheme
            // This is more reliable than JavaScript redirect for WebAuth browsers
            String redirectUrl = String.format("orbvpn://login?token=%s", authenticatedUser.getAccessToken());
            response.sendRedirect(redirectUrl);
        } else {
            System.out.println("Request is from a web browser.");
            // For web app, use postMessage to send the token and close the popup
            String script = String.format(
                    "<html><body><script>" +
                            "window.opener.postMessage({ token: '%s' }, '*');" +
                            "window.close();" +
                            "</script></body></html>",
                    authenticatedUser.getAccessToken());
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(script);
            response.getWriter().flush(); // Ensure the writer is flushed
            response.getWriter().close(); // Ensure the writer is closed
        }
    }

    public boolean isMobile(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return false;
        }

        userAgent = userAgent.toLowerCase();
        System.out.println("User-Agent: " + userAgent); // Log the User-Agent for debugging

        // Check for mobile indicators in User-Agent
        // Note: Chrome Custom Tabs on Android send User-Agent like:
        // "Mozilla/5.0 (Linux; Android 14; ...) AppleWebKit/537.36 ... Chrome/... Mobile Safari/..."
        // The key indicators are "android", "iphone", "ipad", or "mobile"
        boolean hasMobileIndicator = userAgent.contains("mobi") ||
                                     userAgent.contains("android") ||
                                     userAgent.contains("iphone") ||
                                     userAgent.contains("ipad");

        // Also check X-Requested-With header which Chrome Custom Tabs may set
        String requestedWith = request.getHeader("X-Requested-With");
        boolean isCustomTab = requestedWith != null &&
                             (requestedWith.contains("com.android.chrome") ||
                              requestedWith.contains("com.orb"));

        System.out.println("isMobile check: hasMobileIndicator=" + hasMobileIndicator +
                          ", isCustomTab=" + isCustomTab + ", X-Requested-With=" + requestedWith);

        return hasMobileIndicator || isCustomTab;
    }
}