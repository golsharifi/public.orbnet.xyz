package com.orbvpn.api.service.social_login;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.orbvpn.api.config.security.JwtTokenUtil;
import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.OauthToken;
import com.orbvpn.api.domain.entity.Role;
import com.orbvpn.api.domain.entity.User;
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
import java.util.Collections;
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

    public AuthenticatedUser oauthLogin(String token, SocialMedia socialMedia) {

        TokenData tokenData = getTokenData(token, socialMedia);

        User user = userRepository.findByUsername(tokenData.getEmail())
                .orElseGet(() -> createUser(tokenData));

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

        return userService.loginInfo(user);
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
                return getAmazonTokenData(token);
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

        userRepository.save(user);

        userService.assignTrialSubscription(user);

        // Call the new method without a subscription
        notificationService.welcomingNewUsersWithoutSubscription(user, password);
        webhookService.processWebhook("USER_CREATED",
                webhookEventCreator.createUserPayload(user, "USER_CREATED"));
        return user;
    }

    private TokenData getGoogleTokenData(String token) {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idTokenData;
        try {
            idTokenData = verifier.verify(token);
        } catch (Exception ex) {
            log.error(String.format("Exception occurred while verifying Google user token : %s", ex.getCause()));
            throw new OauthLoginException();
        }

        if (idTokenData == null) {
            log.error("idTokenData is null");
            throw new OauthLoginException();
        }

        Payload payload = idTokenData.getPayload();

        String email = payload.getEmail();
        long exp = payload.getExpirationTimeSeconds();
        long iat = payload.getIssuedAtTimeSeconds();

        return TokenData.builder()
                .email(email)
                .exp(exp)
                .iat(iat)
                .oauthId("GOOGLE")
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
        String dataUrl = MessageFormat.format("https://graph.facebook.com/me?fields=email&access_token={0}", token);
        ResponseEntity<FBTokenData> fbTokenDataResponse = dataRequest.getForEntity(dataUrl, FBTokenData.class);
        FBTokenData fbTokenData = fbTokenDataResponse.getBody();

        return TokenData.builder()
                .email(fbTokenData.getEmail())
                .oauthId(fbTokenData.getId())
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
            JwkProvider provider = new UrlJwkProvider(new URL("https://appleid.apple.com/auth/keys"));
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
        // RestTemplate restTemplate = new RestTemplate();
        // HttpHeaders headers = new HttpHeaders();
        //// headers.add(HttpHeaders.AUTHORIZATION, "Bearer " +encryptedToken);
        //
        // UriComponentsBuilder builder =
        // UriComponentsBuilder.fromHttpUrl(linkedinEmailURL)
        // .queryParam("oauth2_access_token", encryptedToken)
        // .queryParam("q", "members")
        // .queryParam("projection", "(elements*(primary,type,handle~))");
        //
        // HttpEntity<?> entity = new HttpEntity<>(headers);
        //
        // try {
        //
        // HttpEntity<ObjectNode> response = restTemplate.exchange(
        // builder.toUriString(),
        // HttpMethod.GET,
        // entity,
        // ObjectNode.class);
        //
        // email =
        // response.getBody().get("elements").get(0).get("handle~").get("emailAddress").asText();
        // } catch (Exception ex) {
        //
        // throw new OauthLoginException(ex.getMessage());
        // }
        String email = null;
        Long iat = 0L;
        Long exp = 0L;
        try {
            DecodedJWT jwt = JWT.decode(encryptedToken);
            email = jwt.getClaim("email").asString();
            iat = jwt.getClaim("iat").asLong();
            exp = jwt.getClaim("exp").asLong();
            Long currentTimestamp = Instant.now().getEpochSecond();
            if (exp < currentTimestamp) {
                throw new OauthLoginException("Token expiration");
            }
        } catch (Exception ex) {
            throw new OauthLoginException(ex.getMessage());
        }

        return TokenData.builder()
                .iat(iat)
                .exp(exp)
                .email(email)
                .oauthId("LINKEDIN")
                .build();
    }

    private TokenData getAmazonTokenData(String encryptedToken) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + encryptedToken);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(amazonProfileURL);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                request,
                Map.class);
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

    private TokenData getGithubTokenData(String encryptedToken) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "token " + encryptedToken);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(githubProfileURL);

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
