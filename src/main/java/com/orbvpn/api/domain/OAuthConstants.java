package com.orbvpn.api.domain;

import com.orbvpn.api.config.DynamicOAuthConfig.OAuthRedirectResolver;
import com.orbvpn.api.utils.AppleSecretGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OAuthConstants {

    private static final Logger log = LoggerFactory.getLogger(OAuthConstants.class);

    public static String googleClientId;
    public static String googleTokenURL;
    public static String googleRedirectURL;
    public static String googleClientSecret;
    public static List<String> googleValidClientIds;

    public static String facebookAppId;
    public static String facebookAppSecret;
    public static String facebookRedirectURL;

    public static String twitterClientId;
    public static String twitterClientSecret;
    public static String twitterCallbackUrl;
    public static String twitterUserInfoUrl;

    public static String linkedinClientId;
    public static String linkedinClientSecret;
    public static String linkedinRedirectURL;
    public static String linkedinTokenURL;
    public static String linkedinProfileURL;
    public static String linkedinEmailURL;

    public static String appleClientId;
    public static String appleRedirectURL;
    public static String appleTokenURL;

    public static String amazonClientId;
    public static String amazonClientSecret;
    public static String amazonRedirectURL;
    public static String amazonTokenURL;
    public static String amazonProfileURL;

    public static String githubRedirectURL;
    public static String githubProfileURL;

    private static String appleClientSecret;

    // Add static reference for the resolver
    private static OAuthRedirectResolver staticResolver;

    public OAuthConstants(AppleSecretGenerator appleSecretGenerator) {
        // Dynamically setting the Apple client secret and logging the event
        String generatedClientSecret = appleSecretGenerator.createClientSecret();
        log.info("Generated Apple client secret: {}", generatedClientSecret);
        setAppleClientSecret(generatedClientSecret);
    }

    // Initialize the static resolver after dependency injection
    @Autowired(required = false)
    public void setRedirectResolver(OAuthRedirectResolver resolver) {
        staticResolver = resolver;
        log.info("Dynamic OAuth redirect resolver initialized");
    }

    public static String getAppleClientSecret() {
        // Log the access of the appleClientSecret
        log.info("Accessing Apple client secret: {}", appleClientSecret);
        return appleClientSecret;
    }

    public static void setAppleClientSecret(String _appleClientSecret) {
        // Log when the appleClientSecret is set
        log.info("Setting Apple client secret: {}", _appleClientSecret);
        appleClientSecret = _appleClientSecret;
    }

    // Add dynamic getter methods that can be used instead of direct field access
    public static String getDynamicGoogleRedirectURL() {
        if (staticResolver != null) {
            return staticResolver.getRedirectUrl("google");
        }
        return googleRedirectURL;
    }

    public static String getDynamicFacebookRedirectURL() {
        if (staticResolver != null) {
            return staticResolver.getRedirectUrl("facebook");
        }
        return facebookRedirectURL;
    }

    public static String getDynamicTwitterCallbackUrl() {
        if (staticResolver != null) {
            return staticResolver.getRedirectUrl("twitter");
        }
        return twitterCallbackUrl;
    }

    public static String getDynamicLinkedinRedirectURL() {
        if (staticResolver != null) {
            return staticResolver.getRedirectUrl("linkedin");
        }
        return linkedinRedirectURL;
    }

    public static String getDynamicAppleRedirectURL() {
        if (staticResolver != null) {
            return staticResolver.getRedirectUrl("apple");
        }
        return appleRedirectURL;
    }

    public static String getDynamicAmazonRedirectURL() {
        if (staticResolver != null) {
            return staticResolver.getRedirectUrl("amazon");
        }
        return amazonRedirectURL;
    }

    public static String getDynamicGithubRedirectURL() {
        if (staticResolver != null) {
            return staticResolver.getRedirectUrl("github");
        }
        return githubRedirectURL;
    }

    @Value("${oauth.google.client-id}")
    public void setGoogleClientId(String _googleClientId) {
        googleClientId = _googleClientId;
    }

    @Value("${oauth.google.token-url}")
    public void setGoogleTokenURL(String _googleTokenURL) {
        googleTokenURL = _googleTokenURL;
    }

    @Value("${oauth.google.client-secret}")
    public void setGoogleClientSecret(String _googleClientSecret) {
        googleClientSecret = _googleClientSecret;
    }

    @Value("${oauth.google.redirectUri}")
    public void setGoogleRedirectURL(String _googleRedirectURL) {
        googleRedirectURL = _googleRedirectURL;
    }

    @Value("#{T(java.util.Arrays).asList('${oauth.google.valid-client-ids}'.split(','))}")
    public void setGoogleValidClientIds(List<String> _googleValidClientIds) {
        // Trim whitespace from each client ID
        googleValidClientIds = _googleValidClientIds.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
        log.info("Loaded {} valid Google client IDs for token verification", googleValidClientIds.size());
    }

    @Value("${oauth.facebook.app-id}")
    public void setFacebookAppId(String _facebookAppId) {
        facebookAppId = _facebookAppId;
    }

    @Value("${oauth.facebook.app-secret}")
    public void setFacebookAppSecret(String _facebookAppSecret) {
        facebookAppSecret = _facebookAppSecret;
    }

    @Value("${oauth.facebook.redirectUri}")
    public void setFacebookRedirectURL(String _facebookRedirectURL) {
        facebookRedirectURL = _facebookRedirectURL;
    }

    @Value("${oauth.twitter.client-id}")
    public void setTwitterClientId(String _twitterClientId) {
        twitterClientId = _twitterClientId;
    }

    @Value("${oauth.twitter.client-secret}")
    public void setTwitterClientSecret(String _twitterClientSecret) {
        twitterClientSecret = _twitterClientSecret;
    }

    @Value("${oauth.twitter.callbackUrl}")
    public void setTwitterCallbackUrl(String _twitterCallbackUrl) {
        twitterCallbackUrl = _twitterCallbackUrl;
    }

    @Value("${oauth.twitter.userInfoUrl}")
    public void setTwitterUserInfoUrl(String _twitterUserInfoUrl) {
        twitterUserInfoUrl = _twitterUserInfoUrl;
    }

    @Value("${oauth.linkedin.client-id}")
    public void setLinkedinClientId(String _linkedinClientId) {
        linkedinClientId = _linkedinClientId;
    }

    @Value("${oauth.linkedin.client-secret}")
    public void setLinkedinClientSecret(String _linkedinClientSecret) {
        linkedinClientSecret = _linkedinClientSecret;
    }

    @Value("${oauth.linkedin.token-url}")
    public void setLinkedinTokenURL(String _linkedinTokenURL) {
        linkedinTokenURL = _linkedinTokenURL;
    }

    @Value("${oauth.linkedin.redirectUri}")
    public void setLinkedinRedirectURL(String _linkedinRedirectURL) {
        linkedinRedirectURL = _linkedinRedirectURL;
    }

    @Value("${oauth.linkedin.profile-url}")
    public void setLinkedinProfileURL(String _linkedinProfileURL) {
        linkedinProfileURL = _linkedinProfileURL;
    }

    @Value("${oauth.linkedin.email-url}")
    public void setLinkedinEmailURL(String _linkedinEmailURL) {
        linkedinEmailURL = _linkedinEmailURL;
    }

    @Value("${oauth.apple.client-id}")
    public void setAppleClientId(String _appleClientId) {
        appleClientId = _appleClientId;
    }

    @Value("${oauth.apple.redirectUri}")
    public void setAppleRedirectURL(String _appleRedirectURL) {
        appleRedirectURL = _appleRedirectURL;
    }

    @Value("${oauth.apple.token-url}")
    public void setAppleTokenURL(String _appleTokenURL) {
        appleTokenURL = _appleTokenURL;
    }

    @Value("${oauth.amazon.client-id}")
    public void setAmazonClientId(String _amazonClientId) {
        amazonClientId = _amazonClientId;
    }

    @Value("${oauth.amazon.client-secret}")
    public void setAmazonClientSecret(String _amazonClientSecret) {
        amazonClientSecret = _amazonClientSecret;
    }

    @Value("${oauth.amazon.redirectUri}")
    public void setAmazonRedirectURL(String _amazonRedirectURL) {
        amazonRedirectURL = _amazonRedirectURL;
    }

    @Value("${oauth.amazon.token-url}")
    public void setAmazonTokenURL(String _amazonTokenURL) {
        amazonTokenURL = _amazonTokenURL;
    }

    @Value("${oauth.amazon.profile-url}")
    public void setAmazonProfileURL(String _amazonProfileURL) {
        amazonProfileURL = _amazonProfileURL;
    }

    @Value("${oauth.github.redirect-uri}")
    public void setGithubRedirectURL(String _githubRedirectUR) {
        githubRedirectURL = _githubRedirectUR;
    }

    @Value("${oauth.github.profile-url}")
    public void setGithubProfileURL(String _githubProfileURL) {
        githubProfileURL = _githubProfileURL;
    }
}