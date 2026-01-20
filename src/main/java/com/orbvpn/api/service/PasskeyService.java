package com.orbvpn.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.dto.passkey.*;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserPasskey;
import com.orbvpn.api.exception.BadRequestException;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.OauthTokenRepository;
import com.orbvpn.api.repository.UserPasskeyRepository;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.domain.entity.OauthToken;
import com.orbvpn.api.mapper.OAuthConnectionViewMapper;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.authenticator.Authenticator;
import com.webauthn4j.authenticator.AuthenticatorImpl;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.*;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.attestation.statement.AttestationStatement;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.util.Base64UrlUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasskeyService {

    private final UserPasskeyRepository passkeyRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final OauthTokenRepository oauthTokenRepository;
    private final OAuthConnectionViewMapper oauthConnectionViewMapper;

    @Value("${webauthn.rp.id:localhost}")
    private String rpId;

    @Value("${webauthn.rp.name:OrbNet Admin}")
    private String rpName;

    @Value("${webauthn.rp.origin:http://localhost:3000}")
    private String rpOrigin;

    private final WebAuthnManager webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
    private final ObjectConverter objectConverter = new ObjectConverter();
    private final SecureRandom secureRandom = new SecureRandom();

    // In-memory challenge storage (should use Redis in production)
    private final Map<String, ChallengeData> challengeStore = new ConcurrentHashMap<>();

    private static final long CHALLENGE_TIMEOUT_MS = 300000; // 5 minutes

    /**
     * Generate registration options for a new passkey
     */
    @Transactional(readOnly = true)
    public PasskeyRegistrationOptions generateRegistrationOptions(User user, String passkeyName) {
        log.info("Generating passkey registration options for user: {}", user.getEmail());

        // Generate challenge
        byte[] challengeBytes = new byte[32];
        secureRandom.nextBytes(challengeBytes);
        String challenge = Base64UrlUtil.encodeToString(challengeBytes);

        // Store challenge for verification
        String challengeKey = "reg_" + user.getId() + "_" + System.currentTimeMillis();
        challengeStore.put(challengeKey, new ChallengeData(challengeBytes, passkeyName, System.currentTimeMillis()));

        // Get existing credentials to exclude
        List<UserPasskey> existingPasskeys = passkeyRepository.findByUser(user);
        List<PasskeyRegistrationOptions.ExcludeCredential> excludeCredentials = existingPasskeys.stream()
                .map(pk -> PasskeyRegistrationOptions.ExcludeCredential.builder()
                        .id(pk.getCredentialId())
                        .type("public-key")
                        .transports(parseTransports(pk.getTransports()))
                        .build())
                .collect(Collectors.toList());

        // Build user ID from user's ID
        String userId = Base64UrlUtil.encodeToString(String.valueOf(user.getId()).getBytes());

        return PasskeyRegistrationOptions.builder()
                .challenge(challenge)
                .rp(PasskeyRegistrationOptions.RelyingParty.builder()
                        .id(rpId)
                        .name(rpName)
                        .build())
                .user(PasskeyRegistrationOptions.UserInfo.builder()
                        .id(userId)
                        .name(user.getEmail())
                        .displayName(getDisplayName(user))
                        .build())
                .pubKeyCredParams(List.of(
                        PasskeyRegistrationOptions.PubKeyCredParam.builder().type("public-key").alg(-7).build(),   // ES256
                        PasskeyRegistrationOptions.PubKeyCredParam.builder().type("public-key").alg(-257).build()  // RS256
                ))
                .timeout(CHALLENGE_TIMEOUT_MS)
                .attestation("none")
                .authenticatorSelection(PasskeyRegistrationOptions.AuthenticatorSelection.builder()
                        .authenticatorAttachment("platform")
                        .requireResidentKey(true)
                        .residentKey("required")
                        .userVerification("required")
                        .build())
                .excludeCredentials(excludeCredentials)
                .build();
    }

    /**
     * Verify and store a new passkey registration
     */
    @SuppressWarnings("deprecation")
    @Transactional
    public PasskeyView verifyRegistration(User user, PasskeyRegistrationRequest request) {
        log.info("Verifying passkey registration for user: {}", user.getEmail());

        try {
            // Find and validate challenge
            ChallengeData challengeData = findValidChallenge("reg_" + user.getId());
            if (challengeData == null) {
                throw new BadRequestException("Registration challenge expired or not found");
            }

            // Decode the response data
            byte[] clientDataJSON = Base64UrlUtil.decode(request.getResponse().getClientDataJSON());
            byte[] attestationObject = Base64UrlUtil.decode(request.getResponse().getAttestationObject());

            // Create server property
            Origin origin = new Origin(rpOrigin);
            Challenge challenge = new DefaultChallenge(challengeData.challenge);
            ServerProperty serverProperty = new ServerProperty(origin, rpId, challenge, null);

            // Parse registration data
            RegistrationRequest registrationRequest = new RegistrationRequest(attestationObject, clientDataJSON);
            RegistrationParameters registrationParameters = new RegistrationParameters(
                    serverProperty,
                    null,
                    false,
                    true
            );

            // Validate registration
            RegistrationData registrationData = webAuthnManager.parse(registrationRequest);
            webAuthnManager.validate(registrationData, registrationParameters);

            // Extract credential data
            AttestedCredentialData attestedCredentialData = registrationData.getAttestationObject()
                    .getAuthenticatorData().getAttestedCredentialData();

            if (attestedCredentialData == null) {
                throw new BadRequestException("No attested credential data found");
            }

            byte[] credentialId = attestedCredentialData.getCredentialId();
            String credentialIdBase64 = Base64UrlUtil.encodeToString(credentialId);

            // Check if credential already exists
            if (passkeyRepository.existsByCredentialId(credentialIdBase64)) {
                throw new BadRequestException("This passkey is already registered");
            }

            // Serialize public key
            COSEKey coseKey = attestedCredentialData.getCOSEKey();
            byte[] publicKeyBytes = objectConverter.getCborConverter().writeValueAsBytes(coseKey);
            String publicKeyCose = Base64.getEncoder().encodeToString(publicKeyBytes);

            // Get AAGUID
            String aaguid = attestedCredentialData.getAaguid() != null ?
                    attestedCredentialData.getAaguid().toString() : null;

            // Determine device type and backup status from flags
            AuthenticatorData<?> authData = registrationData.getAttestationObject().getAuthenticatorData();
            boolean isBackedUp = authData.isFlagBS();
            String deviceType = determineDeviceType(request.getAuthenticatorAttachment());

            // Get attestation format
            AttestationStatement attestationStatement = registrationData.getAttestationObject().getAttestationStatement();
            String attestationFormat = attestationStatement != null ? attestationStatement.getFormat() : "none";

            // Create and save passkey
            UserPasskey passkey = UserPasskey.builder()
                    .user(user)
                    .credentialId(credentialIdBase64)
                    .publicKeyCose(publicKeyCose)
                    .name(challengeData.passkeyName != null ? challengeData.passkeyName : "Passkey")
                    .aaguid(aaguid)
                    .signCount(authData.getSignCount())
                    .transports(serializeTransports(request.getResponse().getTransports()))
                    .attestationFormat(attestationFormat)
                    .deviceType(deviceType)
                    .backedUp(isBackedUp)
                    .build();

            UserPasskey savedPasskey = passkeyRepository.save(passkey);
            log.info("Successfully registered passkey '{}' for user: {}", savedPasskey.getName(), user.getEmail());

            // Clean up challenge
            cleanupChallenges("reg_" + user.getId());

            return mapToView(savedPasskey);

        } catch (Exception e) {
            log.error("Passkey registration failed for user: {}", user.getEmail(), e);
            throw new BadRequestException("Passkey registration failed: " + e.getMessage());
        }
    }

    /**
     * Generate authentication options
     */
    public PasskeyAuthenticationOptions generateAuthenticationOptions(String email) {
        log.info("Generating passkey authentication options for email: {}", email);

        // Find user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Get user's passkeys
        List<UserPasskey> passkeys = passkeyRepository.findByUser(user);
        if (passkeys.isEmpty()) {
            throw new BadRequestException("No passkeys registered for this user");
        }

        // Generate challenge
        byte[] challengeBytes = new byte[32];
        secureRandom.nextBytes(challengeBytes);
        String challenge = Base64UrlUtil.encodeToString(challengeBytes);

        // Store challenge
        String challengeKey = "auth_" + user.getId() + "_" + System.currentTimeMillis();
        challengeStore.put(challengeKey, new ChallengeData(challengeBytes, null, System.currentTimeMillis()));

        // Build allow credentials list
        List<PasskeyAuthenticationOptions.AllowCredential> allowCredentials = passkeys.stream()
                .map(pk -> PasskeyAuthenticationOptions.AllowCredential.builder()
                        .id(pk.getCredentialId())
                        .type("public-key")
                        .transports(parseTransports(pk.getTransports()))
                        .build())
                .collect(Collectors.toList());

        return PasskeyAuthenticationOptions.builder()
                .challenge(challenge)
                .timeout(CHALLENGE_TIMEOUT_MS)
                .rpId(rpId)
                .allowCredentials(allowCredentials)
                .userVerification("required")
                .build();
    }

    /**
     * Generate authentication options without email (for discoverable credentials)
     */
    public PasskeyAuthenticationOptions generateAuthenticationOptionsDiscoverable() {
        log.info("Generating discoverable passkey authentication options");

        // Generate challenge
        byte[] challengeBytes = new byte[32];
        secureRandom.nextBytes(challengeBytes);
        String challenge = Base64UrlUtil.encodeToString(challengeBytes);

        // Store challenge
        String challengeKey = "auth_discoverable_" + System.currentTimeMillis();
        challengeStore.put(challengeKey, new ChallengeData(challengeBytes, null, System.currentTimeMillis()));

        return PasskeyAuthenticationOptions.builder()
                .challenge(challenge)
                .timeout(CHALLENGE_TIMEOUT_MS)
                .rpId(rpId)
                .allowCredentials(Collections.emptyList()) // Empty for discoverable credentials
                .userVerification("required")
                .build();
    }

    /**
     * Verify authentication and return the authenticated user
     */
    @SuppressWarnings("deprecation")
    @Transactional
    public User verifyAuthentication(PasskeyAuthenticationRequest request) {
        log.info("Verifying passkey authentication");

        try {
            String credentialId = request.getId();

            // Find the passkey
            UserPasskey passkey = passkeyRepository.findByCredentialId(credentialId)
                    .orElseThrow(() -> new BadRequestException("Passkey not found"));

            User user = passkey.getUser();

            // Find valid challenge
            ChallengeData challengeData = findValidChallenge("auth_" + user.getId());
            if (challengeData == null) {
                // Try discoverable challenge
                challengeData = findValidChallenge("auth_discoverable");
            }
            if (challengeData == null) {
                throw new BadRequestException("Authentication challenge expired or not found");
            }

            // Decode response data
            byte[] clientDataJSON = Base64UrlUtil.decode(request.getResponse().getClientDataJSON());
            byte[] authenticatorData = Base64UrlUtil.decode(request.getResponse().getAuthenticatorData());
            byte[] signature = Base64UrlUtil.decode(request.getResponse().getSignature());

            // Create server property
            Origin origin = new Origin(rpOrigin);
            Challenge challenge = new DefaultChallenge(challengeData.challenge);
            ServerProperty serverProperty = new ServerProperty(origin, rpId, challenge, null);

            // Reconstruct the authenticator
            byte[] publicKeyBytes = Base64.getDecoder().decode(passkey.getPublicKeyCose());
            COSEKey coseKey = objectConverter.getCborConverter().readValue(publicKeyBytes, COSEKey.class);

            byte[] credentialIdBytes = Base64UrlUtil.decode(passkey.getCredentialId());
            AttestedCredentialData attestedCredentialData = new AttestedCredentialData(
                    null, // AAGUID not needed for authentication
                    credentialIdBytes,
                    coseKey
            );

            Authenticator authenticator = new AuthenticatorImpl(
                    attestedCredentialData,
                    null,
                    passkey.getSignCount()
            );

            // Parse authentication data
            AuthenticationRequest authenticationRequest = new AuthenticationRequest(
                    credentialIdBytes,
                    null, // userHandle
                    authenticatorData,
                    clientDataJSON,
                    signature
            );

            AuthenticationParameters authenticationParameters = new AuthenticationParameters(
                    serverProperty,
                    authenticator,
                    null,
                    true
            );

            // Validate authentication
            AuthenticationData authenticationData = webAuthnManager.parse(authenticationRequest);
            webAuthnManager.validate(authenticationData, authenticationParameters);

            // Update sign count
            passkey.setSignCount(authenticationData.getAuthenticatorData().getSignCount());
            passkey.setLastUsedAt(LocalDateTime.now());
            passkeyRepository.save(passkey);

            // Clean up challenges
            cleanupChallenges("auth_" + user.getId());
            cleanupChallenges("auth_discoverable");

            log.info("Successfully authenticated user {} with passkey '{}'", user.getEmail(), passkey.getName());
            return user;

        } catch (Exception e) {
            log.error("Passkey authentication failed", e);
            throw new BadRequestException("Passkey authentication failed: " + e.getMessage());
        }
    }

    /**
     * Get all passkeys for a user
     */
    @Transactional(readOnly = true)
    public List<PasskeyView> getUserPasskeys(User user) {
        return passkeyRepository.findByUser(user).stream()
                .map(this::mapToView)
                .collect(Collectors.toList());
    }

    /**
     * Admin: Get all passkeys for a user by user ID
     */
    @Transactional(readOnly = true)
    public List<PasskeyView> getPasskeysByUserId(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return passkeyRepository.findByUser(user).stream()
                .map(this::mapToView)
                .collect(Collectors.toList());
    }

    /**
     * Admin: Delete a user's passkey
     */
    @Transactional
    public boolean deletePasskeyAsAdmin(Integer userId, Long passkeyId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        UserPasskey passkey = passkeyRepository.findById(passkeyId)
                .orElseThrow(() -> new NotFoundException("Passkey not found"));

        if (passkey.getUser().getId() != user.getId()) {
            throw new BadRequestException("Passkey does not belong to this user");
        }

        passkeyRepository.delete(passkey);
        log.info("Admin deleted passkey '{}' for user: {}", passkey.getName(), user.getEmail());
        return true;
    }

    /**
     * Delete a passkey
     */
    @Transactional
    public boolean deletePasskey(User user, Long passkeyId) {
        UserPasskey passkey = passkeyRepository.findById(passkeyId)
                .orElseThrow(() -> new NotFoundException("Passkey not found"));

        if (passkey.getUser().getId() != user.getId()) {
            throw new BadRequestException("You can only delete your own passkeys");
        }

        // Check if this is the last passkey - prevent deletion if it's the only auth method
        long passkeyCount = passkeyRepository.countByUser(user);
        if (passkeyCount <= 1) {
            log.warn("Preventing deletion of last passkey for user: {}", user.getEmail());
            // Allow deletion - user can still use password
        }

        passkeyRepository.delete(passkey);
        log.info("Deleted passkey '{}' for user: {}", passkey.getName(), user.getEmail());
        return true;
    }

    /**
     * Rename a passkey
     */
    @Transactional
    public PasskeyView renamePasskey(User user, Long passkeyId, String newName) {
        UserPasskey passkey = passkeyRepository.findById(passkeyId)
                .orElseThrow(() -> new NotFoundException("Passkey not found"));

        if (passkey.getUser().getId() != user.getId()) {
            throw new BadRequestException("You can only rename your own passkeys");
        }

        passkey.setName(newName);
        UserPasskey saved = passkeyRepository.save(passkey);
        log.info("Renamed passkey to '{}' for user: {}", newName, user.getEmail());
        return mapToView(saved);
    }

    // Helper methods

    private PasskeyView mapToView(UserPasskey passkey) {
        return PasskeyView.builder()
                .id(passkey.getId())
                .credentialId(passkey.getCredentialId())
                .name(passkey.getName())
                .deviceType(passkey.getDeviceType())
                .backedUp(passkey.getBackedUp())
                .transports(parseTransports(passkey.getTransports()))
                .lastUsedAt(passkey.getLastUsedAt())
                .createdAt(passkey.getCreatedAt())
                .build();
    }

    private String getDisplayName(User user) {
        if (user.getProfile() != null) {
            String firstName = user.getProfile().getFirstName();
            String lastName = user.getProfile().getLastName();
            if (firstName != null && lastName != null) {
                return firstName + " " + lastName;
            } else if (firstName != null) {
                return firstName;
            }
        }
        return user.getEmail();
    }

    private String determineDeviceType(String authenticatorAttachment) {
        if (authenticatorAttachment == null) return "unknown";
        return switch (authenticatorAttachment) {
            case "platform" -> "platform";
            case "cross-platform" -> "security-key";
            default -> "unknown";
        };
    }

    @SuppressWarnings("unchecked")
    private List<String> parseTransports(String transports) {
        if (transports == null || transports.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(transports, List.class);
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private String serializeTransports(List<String> transports) {
        if (transports == null || transports.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(transports);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private ChallengeData findValidChallenge(String prefix) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ChallengeData> entry : challengeStore.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                ChallengeData data = entry.getValue();
                if (now - data.timestamp < CHALLENGE_TIMEOUT_MS) {
                    return data;
                }
            }
        }
        return null;
    }

    private void cleanupChallenges(String prefix) {
        challengeStore.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
    }

    // ========== OAuth Connection Methods ==========

    /**
     * Admin: Get all OAuth connections for a user by user ID
     */
    @Transactional(readOnly = true)
    public List<OAuthConnectionView> getOAuthConnectionsByUserId(Integer userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return oauthTokenRepository.findAllByUserId(userId).stream()
                .map(oauthConnectionViewMapper::toView)
                .collect(Collectors.toList());
    }

    /**
     * Admin: Delete a user's OAuth connection
     */
    @Transactional
    public boolean deleteOAuthConnectionAsAdmin(Integer userId, Integer connectionId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        OauthToken oauthToken = oauthTokenRepository.findById(connectionId)
                .orElseThrow(() -> new NotFoundException("OAuth connection not found"));

        if (oauthToken.getUser().getId() != user.getId()) {
            throw new BadRequestException("OAuth connection does not belong to this user");
        }

        oauthTokenRepository.delete(oauthToken);
        log.info("Admin deleted OAuth connection '{}' for user: {}", oauthToken.getSocialMedia(), user.getEmail());
        return true;
    }

    // Inner class for challenge storage
    private record ChallengeData(byte[] challenge, String passkeyName, long timestamp) {}
}
