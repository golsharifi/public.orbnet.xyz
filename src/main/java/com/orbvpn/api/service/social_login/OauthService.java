package com.orbvpn.api.service.social_login;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.net.URI;
import java.security.interfaces.RSAPublicKey;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.OauthToken;
import com.orbvpn.api.domain.entity.Role;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserProfile;
import com.orbvpn.api.domain.enums.RoleName;
import com.orbvpn.api.domain.enums.SocialMedia;
import com.orbvpn.api.exception.OauthLoginException;
import com.orbvpn.api.repository.OauthTokenRepository;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.service.PasswordService;
import com.orbvpn.api.service.RoleService;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.notification.NotificationService;
import com.orbvpn.api.service.reseller.ResellerService;
import com.orbvpn.api.service.webhook.WebhookEventCreator;
import com.orbvpn.api.service.webhook.WebhookService;
import com.orbvpn.api.service.UserUuidService;
import com.orbvpn.api.service.AsyncNotificationHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.social.oauth1.AuthorizedRequestToken;
import org.springframework.social.oauth1.OAuth1Operations;
import org.springframework.social.oauth1.OAuth1Parameters;
import org.springframework.social.oauth1.OAuthToken;
import org.springframework.social.twitter.api.impl.TwitterTemplate;
import org.springframework.social.twitter.connect.TwitterConnectionFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.orbvpn.api.domain.OAuthConstants.*;

@RequiredArgsConstructor
@Service
@Slf4j
public class OauthService {

    private static final HttpTransport transport = new NetHttpTransport();
    private static final JsonFactory jsonFactory = new GsonFactory();

    private final RoleService roleService;
    private final UserService userService;
    private final PasswordService passwordService;
    private final ResellerService resellerService;
    private final UserRepository userRepository;
    private final OauthTokenRepository oauthTokenRepository;
    private final TokenServiceV2 tokenService;
    private final NotificationService notificationService;
    private final WebhookService webhookService;
    private final WebhookEventCreator webhookEventCreator;
    private final UserUuidService userUuidService;
    private final AsyncNotificationHelper asyncNotificationHelper;

    public AuthenticatedUser oauthLogin(String token, SocialMedia socialMedia) {
        log.info("Starting OAuth login for socialMedia: {}", socialMedia);

        TokenData tokenData = getTokenData(token, socialMedia);
        log.info("Token data retrieved for email: {}", tokenData.getEmail());

        boolean isNewUser = !userRepository.findByUsername(tokenData.getEmail()).isPresent();
        User user = userRepository.findByUsername(tokenData.getEmail())
                .orElseGet(() -> createUser(tokenData));
        log.info("User {} - ID: {}, Email: {}", isNewUser ? "CREATED" : "FOUND", user.getId(), user.getEmail());

        // Save data to oauth_token
        Optional<OauthToken> optionalOauthToken = oauthTokenRepository.findByUserIdAndSocialMedia(user.getId(),
                socialMedia);
        if (optionalOauthToken.isPresent()) {
            OauthToken oauthToken = optionalOauthToken.get();
            oauthToken.setToken(token);
            oauthToken.setSocialToken(token);
            oauthToken.setIat(tokenData.getIat());
            oauthToken.setExp(tokenData.getExp());

            oauthTokenRepository.save(oauthToken);
        } else {
            OauthToken oauthToken = new OauthToken();
            oauthToken.setToken(token);
            oauthToken.setSocialToken(token);
            oauthToken.setIat(tokenData.getIat());
            oauthToken.setExp(tokenData.getExp());
            oauthToken.setUser(user);
            oauthToken.setSocialMedia(socialMedia);
            oauthTokenRepository.save(oauthToken);
        }

        log.info("OAuth token saved, generating login info for user ID: {}", user.getId());
        AuthenticatedUser result = userService.loginInfo(user);
        log.info("OAuth login completed successfully for user ID: {}, accessToken generated: {}",
                user.getId(), result.getAccessToken() != null);
        return result;
    }

    public AuthenticatedUser getTokenAndLogin(String code, SocialMedia socialMedia) {

        String token;

        switch (socialMedia) {
            case GOOGLE:
                token = tokenService.getGoogleToken(code);
                break;
            case FACEBOOK:
                token = tokenService.getFacebookToken(code);
                break;
            case APPLE:
                token = tokenService.getAppleToken(code);
                break;
            case LINKEDIN:
                token = tokenService.getLinkedinToken(code);
                break;
            case AMAZON:
                token = tokenService.getAmazonToken(code);
                break;
            case GITHUB:
                token = tokenService.getGithubToken(code);
                break;
            default:
                throw new OauthLoginException("Unknown Token provider.");
        }

        return oauthLogin(token, socialMedia);
    }

    public TokenData getTokenData(String token, SocialMedia socialMedia) {
        switch (socialMedia) {
            case GOOGLE:
                return getGoogleTokenData(token);
            case FACEBOOK:
                return getFacebookTokenData(token);
            case APPLE:
                return getAppleTokenData(token);
            case LINKEDIN:
                return getLinkedinTokenData(token);
            case AMAZON:
                return getAmazonTokenData(token);
            case TWITTER:
                return getTwitterTokenData(token);
            case GITHUB:
                return getGithubTokenData(token);
            default:
                throw new OauthLoginException();
        }
    }

    private User createUser(TokenData tokenData) {
        String password = userService.generateRandomString();

        User user = new User();
        user.setUsername(tokenData.getEmail());
        user.setEmail(tokenData.getEmail());
        passwordService.setPassword(user, password);
        Role role = roleService.getByName(RoleName.USER);
        user.setRole(role);
        user.setReseller(resellerService.getOwnerReseller());
        user.setOauthId(tokenData.getOauthId());

        // Create UserProfile with OAuth data
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        if (tokenData.getFirstName() != null) {
            profile.setFirstName(tokenData.getFirstName());
        }
        if (tokenData.getLastName() != null) {
            profile.setLastName(tokenData.getLastName());
        }
        user.setProfile(profile);

        userRepository.save(user);

        // Generate UUID for the OAuth user
        try {
            String uuid = userUuidService.getOrCreateUuid(user.getId());
            log.debug("Generated UUID {} for OAuth user {}", uuid, user.getId());
            user.setUuid(uuid);
            user = userRepository.save(user);
        } catch (Exception e) {
            log.error("Failed to generate UUID for OAuth user {}: {}", user.getId(), e.getMessage(), e);
            // Continue without UUID - it can be generated later
            log.warn("Continuing OAuth user creation without UUID for user {}", user.getId());
        }

        userService.assignTrialSubscription(user);

        // Send notifications asynchronously to avoid blocking the OAuth response
        asyncNotificationHelper.sendWelcomeEmailNoSubscriptionAsync(user, password);
        asyncNotificationHelper.sendUserWebhookAsync(user, "USER_CREATED");

        log.info("Created OAuth user with ID: {}, UUID: {}, profile name: {} {}",
                user.getId(), user.getUuid(), profile.getFirstName(), profile.getLastName());
        return user;
    }

    private TokenData getGoogleTokenData(String token) {
        // Use valid client IDs from application.yaml configuration
        // This includes: web client, Android (debug/release), iOS, and legacy Firebase client IDs
        List<String> validAudiences = googleValidClientIds;

        if (validAudiences == null || validAudiences.isEmpty()) {
            log.error("No valid Google client IDs configured. Check oauth.google.valid-client-ids in application.yaml");
            throw new OauthLoginException("Server configuration error: No valid Google client IDs configured");
        }

        log.info("Verifying Google token against {} valid client IDs: {}", validAudiences.size(), validAudiences);

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                .setAudience(validAudiences)
                .build();

        GoogleIdToken idTokenData;
        try {
            idTokenData = verifier.verify(token);
        } catch (Exception ex) {
            log.error("Exception occurred while verifying Google user token: {}", ex.getMessage(), ex);
            throw new OauthLoginException("Failed to verify Google token: " + ex.getMessage());
        }

        if (idTokenData == null) {
            // Token verification failed - likely audience mismatch
            // Log the token's audience for debugging
            try {
                GoogleIdToken unverifiedToken = GoogleIdToken.parse(jsonFactory, token);
                if (unverifiedToken != null) {
                    Object tokenAudience = unverifiedToken.getPayload().getAudience();
                    log.error("Google token verification failed. Token audience: {}, Valid audiences: {}",
                            tokenAudience, validAudiences);
                }
            } catch (Exception parseEx) {
                log.error("Failed to parse token for debugging: {}", parseEx.getMessage());
            }
            throw new OauthLoginException("Invalid Google token - verification failed");
        }

        Payload payload = idTokenData.getPayload();

        String email = payload.getEmail();
        long exp = payload.getExpirationTimeSeconds();
        long iat = payload.getIssuedAtTimeSeconds();

        // Extract name information from Google payload
        String firstName = (String) payload.get("given_name");
        String lastName = (String) payload.get("family_name");
        String fullName = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");

        log.info("Google token verified successfully for email: {}, name: {} {}", email, firstName, lastName);

        return TokenData.builder()
                .email(email)
                .exp(exp)
                .iat(iat)
                .oauthId("GOOGLE")
                .firstName(firstName)
                .lastName(lastName)
                .fullName(fullName)
                .pictureUrl(pictureUrl)
                .build();
    }

    private TokenData getFacebookTokenData(String token) {
        RestTemplate debugRequest = new RestTemplate();
        String debugUrl = MessageFormat.format(
                "https://graph.facebook.com/debug_token?input_token={0}&access_token={1}|{2}", token, facebookAppId,
                facebookAppSecret);
        ResponseEntity<FBTokenMetadataWrapper> fbTokenMetadataWrapperResponse = debugRequest.getForEntity(debugUrl,
                FBTokenMetadataWrapper.class);

        FBTokenMetadata fbTokenMetadata = fbTokenMetadataWrapperResponse.getBody().getData();

        if (fbTokenMetadata == null) {
            throw new OauthLoginException();
        }

        String appId = fbTokenMetadata.getAppId();

        if (appId == null || !appId.equals(facebookAppId)) {
            throw new OauthLoginException();
        }

        RestTemplate dataRequest = new RestTemplate();
        // Request email, first_name, last_name, and name from Facebook Graph API
        String dataUrl = MessageFormat.format("https://graph.facebook.com/me?fields=email,first_name,last_name,name,picture&access_token={0}", token);
        ResponseEntity<FBTokenData> fbTokenDataResponse = dataRequest.getForEntity(dataUrl, FBTokenData.class);
        FBTokenData fbTokenData = fbTokenDataResponse.getBody();

        return TokenData.builder()
                .email(fbTokenData.getEmail())
                .oauthId(fbTokenData.getId())
                .firstName(fbTokenData.getFirstName())
                .lastName(fbTokenData.getLastName())
                .fullName(fbTokenData.getName())
                .build();

    }

    private TokenData getAppleTokenData(String encryptedToken) {
        try {
            // Log the received encrypted token
            log.info("Received encrypted Apple token: {}", encryptedToken);

            // Decode the token to extract key information
            DecodedJWT jwt = JWT.decode(encryptedToken);
            log.info("Decoded JWT token: {}", jwt);

            // Log the key ID used for token validation
            String keyId = jwt.getKeyId();
            log.info("Extracted Key ID from JWT: {}", keyId);

            // Retrieve the public key from Apple's JWK set URL
            JwkProvider provider = new UrlJwkProvider(new URI("https://appleid.apple.com/auth/keys").toURL());
            Jwk jwk = provider.get(keyId);
            log.info("Retrieved JWK from Apple's key set URL for Key ID: {}", keyId);

            // Use the public key to verify the token
            RSAPublicKey publicKey = (RSAPublicKey) jwk.getPublicKey();
            Algorithm algorithm = Algorithm.RSA256(publicKey, null);
            algorithm.verify(jwt);
            log.info("JWT verified successfully using RSA256 algorithm.");

            // Extract claims from the token (email, exp, iat)
            log.info("Extracting claims from the token...");
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(encryptedToken)
                    .getPayload();

            // Extract and log the email, exp, iat claims
            String email = claims.get("email", String.class);
            Long exp = claims.getExpiration().getTime();
            Long iat = claims.getIssuedAt().getTime();

            // If email is null, retrieve it from the existing user record
            if (email == null) {
                String oauthId = claims.getSubject();
                Optional<User> existingUser = userRepository.findByOauthId(oauthId);
                if (existingUser.isPresent()) {
                    email = existingUser.get().getEmail();
                    log.info("Retrieved email from existing user record: {}", email);
                } else {
                    log.error("Email claim is missing and no existing user found with oauthId: {}", oauthId);
                    throw new OauthLoginException("Email claim is missing and no existing user found.");
                }
            }

            log.info("Extracted claims - Email: {}, Expiration: {}, Issued At: {}", email, exp, iat);

            // Return the TokenData object
            return TokenData.builder()
                    .email(email)
                    .exp(exp)
                    .iat(iat)
                    .oauthId("APPLE")
                    .build();

        } catch (Exception ex) {
            // Log the error message in detail if there's any exception
            log.error("Error while verifying Apple token: {}", ex.getMessage(), ex);
            throw new OauthLoginException("Error while verifying Apple token: " + ex.getMessage());
        }
    }

    private TokenData getLinkedinTokenData(String encryptedToken) {
        try {
            // Decode the token to extract key information
            DecodedJWT jwt = JWT.decode(encryptedToken);
            log.info("Decoded LinkedIn JWT token, Key ID: {}", jwt.getKeyId());

            // Retrieve the public key from LinkedIn's JWK set URL
            JwkProvider provider = new UrlJwkProvider(new URI("https://www.linkedin.com/oauth/openid/jwks").toURL());
            Jwk jwk = provider.get(jwt.getKeyId());
            log.info("Retrieved JWK from LinkedIn's key set URL for Key ID: {}", jwt.getKeyId());

            // Use the public key to verify the token signature
            RSAPublicKey publicKey = (RSAPublicKey) jwk.getPublicKey();
            Algorithm algorithm = Algorithm.RSA256(publicKey, null);
            algorithm.verify(jwt);
            log.info("LinkedIn JWT verified successfully using RSA256 algorithm.");

            // Extract claims from the verified token
            String email = jwt.getClaim("email").asString();
            Long iat = jwt.getClaim("iat").asLong();
            Long exp = jwt.getClaim("exp").asLong();

            // Verify token is not expired
            Long currentTimestamp = Instant.now().getEpochSecond();
            if (exp != null && exp < currentTimestamp) {
                throw new OauthLoginException("LinkedIn token has expired");
            }

            // If email is null, try to get it from existing user record
            if (email == null) {
                String subject = jwt.getSubject();
                if (subject != null) {
                    Optional<User> existingUser = userRepository.findByOauthId(subject);
                    if (existingUser.isPresent()) {
                        email = existingUser.get().getEmail();
                        log.info("Retrieved email from existing user record for LinkedIn subject: {}", subject);
                    } else {
                        log.error("Email claim is missing and no existing user found with LinkedIn subject: {}", subject);
                        throw new OauthLoginException("Email claim is missing from LinkedIn token");
                    }
                }
            }

            log.info("Extracted LinkedIn claims - Email: {}, Expiration: {}, Issued At: {}", email, exp, iat);

            return TokenData.builder()
                    .iat(iat != null ? iat : 0L)
                    .exp(exp != null ? exp : 0L)
                    .email(email)
                    .oauthId("LINKEDIN")
                    .build();

        } catch (OauthLoginException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error while verifying LinkedIn token: {}", ex.getMessage(), ex);
            throw new OauthLoginException("Error while verifying LinkedIn token: " + ex.getMessage());
        }
    }

    private TokenData getAmazonTokenData(String encryptedToken) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + encryptedToken);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(amazonProfileURL);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            String email = (String) response.getBody().get("email");
            return TokenData.builder()
                    // .iat(iat)
                    // .exp(exp)
                    .email(email)
                    .oauthId("AMAZON")
                    .build();
        } else {
            throw new RuntimeException(
                    "Failed to retrieve Amazon access token, status code: " + response.getStatusCode());
        }
    }

    private TokenData getTwitterTokenData(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);

        // Twitter API v2 endpoint to get user info
        String twitterUserMeUrl = "https://api.twitter.com/2/users/me?user.fields=id,name,username";

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    twitterUserMeUrl,
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                if (data != null) {
                    String twitterId = (String) data.get("id");
                    String username = (String) data.get("username");

                    // Twitter doesn't always provide email via API v2 basic endpoint
                    // We need to use the username as identifier or request email scope
                    // For now, use username@twitter.oauth as a fallback email pattern
                    String email = username + "@twitter.oauth";

                    // Check if user exists with this Twitter ID
                    Optional<User> existingUser = userRepository.findByOauthId(twitterId);
                    if (existingUser.isPresent()) {
                        email = existingUser.get().getEmail();
                    }

                    return TokenData.builder()
                            .email(email)
                            .oauthId(twitterId)
                            .build();
                }
            }
            throw new OauthLoginException("Failed to retrieve Twitter user data");
        } catch (Exception ex) {
            log.error("Error while verifying Twitter token: {}", ex.getMessage(), ex);
            throw new OauthLoginException("Error while verifying Twitter token: " + ex.getMessage());
        }
    }

    private TokenData getGithubTokenData(String encryptedToken) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "token " + encryptedToken);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(githubProfileURL);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(headers);

        ResponseEntity<List<GithubProfile>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<List<GithubProfile>>() {
                });

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            List<GithubProfile> githubProfileList = response.getBody();
            for (int i = 0; i < githubProfileList.size(); i++) {
                GithubProfile profile = githubProfileList.get(i);
                if (profile.isPrimary()) {
                    String email = profile.getEmail();
                    return TokenData.builder()
                            // .iat(iat)
                            // .exp(exp)
                            .email(email)
                            .oauthId("GITHUB")
                            .build();
                }
            }

        } else {
            throw new RuntimeException(
                    "Failed to retrieve Amazon access token, status code: " + response.getStatusCode());
        }
        return null;
    }

    public String twitterOauthLogin() {

        TwitterConnectionFactory connectionFactory = new TwitterConnectionFactory(twitterClientId, twitterClientSecret);
        OAuth1Operations oauthOperations = connectionFactory.getOAuthOperations();
        OAuthToken requestToken = oauthOperations.fetchRequestToken(twitterCallbackUrl, null);

        return oauthOperations.buildAuthorizeUrl(requestToken.getValue(), OAuth1Parameters.NONE);
    }

    public AuthenticatedUser twitterUserProfile(HttpServletRequest request, HttpServletResponse response) {

        TwitterConnectionFactory connectionFactory = new TwitterConnectionFactory(twitterClientId, twitterClientSecret);
        OAuth1Operations oauthOperations = connectionFactory.getOAuthOperations();
        OAuthToken oAuthToken = new OAuthToken(request.getParameter("oauth_token"),
                request.getParameter("oauth_verifier"));

        OAuthToken accessToken = oauthOperations.exchangeForAccessToken(
                new AuthorizedRequestToken(oAuthToken, request.getParameter("oauth_verifier")), null);

        TwitterTemplate twitterTemplate = new TwitterTemplate(twitterClientId, twitterClientSecret,
                accessToken.getValue(), accessToken.getSecret());

        RestTemplate restTemplate = twitterTemplate.getRestTemplate();
        ObjectNode objectNode = restTemplate.getForObject(twitterUserInfoUrl, ObjectNode.class);

        String email;
        try {
            email = objectNode.get("email").asText();
        } catch (NullPointerException exception) {
            email = objectNode.get("screen_name").asText();
            log.error("Could not retrieve user email.");
        }

        TokenData tokenData = TokenData.builder()
                .email(email)
                .oauthId("TWITTER")
                .build();

        User user = userRepository.findByUsername(email)
                .orElseGet(() -> createUser(tokenData));

        return userService.loginInfo(user);

    }

}
