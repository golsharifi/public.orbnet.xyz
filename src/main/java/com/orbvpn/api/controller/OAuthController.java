package com.orbvpn.api.controller;

import com.orbvpn.api.domain.dto.AuthenticatedUser;
import com.orbvpn.api.domain.enums.SocialMedia;
import com.orbvpn.api.service.social_login.OauthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;

@RestController
public class OAuthController {

    @Autowired
    private OauthService oauthService;

    @GetMapping("/login/oauth2/code/google")
    public AuthenticatedUser google(@RequestParam("code") String code) {
        return this.oauthService.getTokenAndLogin(code, SocialMedia.GOOGLE);
    }

    @GetMapping("/token/google")
    public AuthenticatedUser getTokenGoogle(@RequestParam("code") String code) {
        return this.oauthService.getTokenAndLogin(code, SocialMedia.GOOGLE);
    }

    @GetMapping("/login/oauth2/code/facebook")
    public void facebook(@RequestParam("code") String code) {
        this.oauthService.getTokenAndLogin(code, SocialMedia.FACEBOOK);
    }

    @GetMapping("/token/facebook")
    public AuthenticatedUser getTokenFacebook(@RequestParam("code") String code) {
        return this.oauthService.getTokenAndLogin(code, SocialMedia.FACEBOOK);
    }

    @GetMapping("/login/oauth2/code/apple")
    public AuthenticatedUser apple(@RequestParam("code") String code) {
        return this.oauthService.getTokenAndLogin(code, SocialMedia.APPLE);
    }

    @GetMapping("/token/apple")
    public AuthenticatedUser getTokenApple(@RequestParam("code") String code) {
        return this.oauthService.getTokenAndLogin(code, SocialMedia.APPLE);
    }

    @GetMapping("/login/manual/oauth2/code/linkedin")
    public ResponseEntity<?> linkedIn(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("code") String code) throws IOException {
        AuthenticatedUser authenticatedUser = this.oauthService.getTokenAndLogin(code, SocialMedia.LINKEDIN);
        // Check if the request is from a mobile app
        if (isMobile(request)) {
            // For mobile app, use custom URL scheme - include both access and refresh tokens
            String redirectUrl = String.format("orbvpn://login?token=%s&refreshToken=%s",
                    authenticatedUser.getAccessToken(),
                    authenticatedUser.getRefreshToken() != null ? authenticatedUser.getRefreshToken() : "");
            response.setContentType("text/html;charset=UTF-8");
            String html = String.format("<html><body><script>window.location.href = '%s';</script></body></html>",
                    redirectUrl);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } else {
            // For web app, redirect to home page with tokens
            String redirectUrl = buildRedirectUrl(request, authenticatedUser.getAccessToken(), authenticatedUser.getRefreshToken());
            response.sendRedirect(redirectUrl);
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
        }
    }

    @GetMapping("/token/linkedin")
    public AuthenticatedUser getTokenLinkedin(@RequestParam("code") String code) {
        return this.oauthService.getTokenAndLogin(code, SocialMedia.LINKEDIN);
    }

    @GetMapping("/oauth2/authorization/manual/twitter")
    public void twitterOauthLogin(HttpServletResponse response) throws IOException {
        String authorizeUrl = oauthService.twitterOauthLogin();
        response.sendRedirect(authorizeUrl);
    }

    @GetMapping("/oauth2/callback/twitter")
    public ResponseEntity<?> getTwitter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthenticatedUser authenticatedUser = oauthService.twitterUserProfile(request, response);
        // Check if the request is from a mobile app
        if (isMobile(request)) {
            // For mobile app, use custom URL scheme - include both access and refresh tokens
            String redirectUrl = String.format("orbvpn://login?token=%s&refreshToken=%s",
                    authenticatedUser.getAccessToken(),
                    authenticatedUser.getRefreshToken() != null ? authenticatedUser.getRefreshToken() : "");
            response.setContentType("text/html;charset=UTF-8");
            String html = String.format("<html><body><script>window.location.href = '%s';</script></body></html>",
                    redirectUrl);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } else {
            // For web app, redirect to home page with tokens
            String redirectUrl = buildRedirectUrl(request, authenticatedUser.getAccessToken(), authenticatedUser.getRefreshToken());
            response.sendRedirect(redirectUrl);
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
        }
    }

    @GetMapping("/token/twitter")
    public AuthenticatedUser getTokenTwitter(@RequestParam("code") String code) {
        return this.oauthService.getTokenAndLogin(code, SocialMedia.TWITTER);
    }

    @GetMapping("/login/oauth2/code/amazon")
    public AuthenticatedUser amazon(@RequestParam("code") String code) {
        return this.oauthService.getTokenAndLogin(code, SocialMedia.AMAZON);
    }

    @GetMapping("/token/amazon")
    public AuthenticatedUser getTokenAmazon(@RequestParam("code") String code) {
        return this.oauthService.getTokenAndLogin(code, SocialMedia.AMAZON);
    }

    @GetMapping("/token/github")
    public AuthenticatedUser getTokenGithub(@RequestParam("code") String code) {
        return this.oauthService.getTokenAndLogin(code, SocialMedia.GITHUB);
    }

    private String buildRedirectUrl(HttpServletRequest request, String accessToken, String refreshToken) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();

        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);

        if ((serverPort != 80) && (serverPort != 443)) {
            url.append(":").append(serverPort);
        }

        url.append(contextPath);

        if (!contextPath.endsWith("/")) {
            url.append("/");
        }

        url.append("?token=").append(accessToken);
        if (refreshToken != null && !refreshToken.isEmpty()) {
            url.append("&refreshToken=").append(refreshToken);
        }

        return url.toString();
    }

    public boolean isMobile(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent").toLowerCase();
        return userAgent.contains("mobi");
    }

}
