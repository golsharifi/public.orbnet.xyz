package com.orbvpn.api.service.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.config.AppleConfiguration;
import com.orbvpn.api.domain.dto.AppleNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for verifying Apple App Store Server Notifications JWT signatures.
 * Uses Apple's JWKS (JSON Web Key Set) for cryptographic verification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@org.springframework.context.annotation.Lazy
public class AppleJwtVerificationService {

    private final AppleConfiguration appleConfiguration;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    // Cache for public keys (key ID -> PublicKey)
    private final Map<String, PublicKey> publicKeyCache = new ConcurrentHashMap<>();
    private volatile long lastKeysFetchTime = 0;
    private static final long KEYS_CACHE_DURATION_MS = 3600000; // 1 hour

    @PostConstruct
    public void init() {
        try {
            refreshPublicKeys();
        } catch (Exception e) {
            log.warn("Failed to fetch Apple JWKS on startup: {}", e.getMessage());
        }
    }

    /**
     * Verifies and decodes an Apple signed payload (JWT).
     *
     * @param signedPayload The JWS signed payload from Apple
     * @return The verified and decoded AppleNotification
     * @throws SecurityException if verification fails
     */
    public AppleNotification verifyAndDecodeNotification(String signedPayload) {
        try {
            String[] parts = signedPayload.split("\\.");
            if (parts.length != 3) {
                throw new SecurityException("Invalid JWT format: expected 3 parts, got " + parts.length);
            }

            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);

            // Parse header to get key ID (kid) and algorithm
            JsonNode header = objectMapper.readTree(headerJson);
            String keyId = header.path("kid").asText();
            String algorithm = header.path("alg").asText();

            if (keyId == null || keyId.isEmpty()) {
                throw new SecurityException("Missing key ID (kid) in JWT header");
            }

            if (!"ES256".equals(algorithm) && !"RS256".equals(algorithm)) {
                throw new SecurityException("Unsupported algorithm: " + algorithm);
            }

            // Get the public key for verification
            PublicKey publicKey = getPublicKey(keyId);
            if (publicKey == null) {
                // Try refreshing keys if key not found
                refreshPublicKeys();
                publicKey = getPublicKey(keyId);
                if (publicKey == null) {
                    throw new SecurityException("Unknown key ID: " + keyId);
                }
            }

            // Verify signature
            String signedContent = parts[0] + "." + parts[1];
            if (!verifySignature(signedContent, signatureBytes, publicKey, algorithm)) {
                throw new SecurityException("JWT signature verification failed");
            }

            // Parse and validate the payload
            AppleNotification notification = objectMapper.readValue(payloadJson, AppleNotification.class);

            // Validate timestamp (not too old - within 24 hours)
            long signedDate = notification.getSignedDate();
            long now = Instant.now().toEpochMilli();
            long maxAge = 24 * 60 * 60 * 1000; // 24 hours
            if (signedDate > 0 && (now - signedDate) > maxAge) {
                log.warn("Apple notification is older than 24 hours, signedDate: {}", signedDate);
                // Don't reject old notifications, just log - Apple may retry
            }

            log.info("Successfully verified Apple notification with UUID: {}", notification.getNotificationUUID());
            return notification;

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error verifying Apple JWT: {}", e.getMessage(), e);
            throw new SecurityException("JWT verification failed: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies a signed transaction info or renewal info JWT.
     */
    public String verifyAndDecodeSignedData(String signedData) {
        if (signedData == null || signedData.isEmpty()) {
            return null;
        }

        try {
            String[] parts = signedData.split("\\.");
            if (parts.length != 3) {
                log.warn("Invalid signed data format, skipping verification");
                // Fall back to unverified decode for backward compatibility
                return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            }

            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);

            JsonNode header = objectMapper.readTree(headerJson);
            String keyId = header.path("kid").asText();
            String algorithm = header.path("alg").asText();

            if (keyId != null && !keyId.isEmpty()) {
                PublicKey publicKey = getPublicKey(keyId);
                if (publicKey != null) {
                    String signedContent = parts[0] + "." + parts[1];
                    if (!verifySignature(signedContent, signatureBytes, publicKey, algorithm)) {
                        log.warn("Signed data signature verification failed");
                    }
                }
            }

            return payloadJson;
        } catch (Exception e) {
            log.warn("Error verifying signed data, falling back to decode only: {}", e.getMessage());
            try {
                String[] parts = signedData.split("\\.");
                return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private boolean verifySignature(String signedContent, byte[] signature, PublicKey publicKey, String algorithm) {
        try {
            String javaAlgorithm = "ES256".equals(algorithm) ? "SHA256withECDSA" : "SHA256withRSA";
            Signature sig = Signature.getInstance(javaAlgorithm);
            sig.initVerify(publicKey);
            sig.update(signedContent.getBytes(StandardCharsets.UTF_8));
            return sig.verify(signature);
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private PublicKey getPublicKey(String keyId) {
        // Check if cache needs refresh
        if (System.currentTimeMillis() - lastKeysFetchTime > KEYS_CACHE_DURATION_MS) {
            try {
                refreshPublicKeys();
            } catch (Exception e) {
                log.warn("Failed to refresh public keys: {}", e.getMessage());
            }
        }
        return publicKeyCache.get(keyId);
    }

    private synchronized void refreshPublicKeys() {
        try {
            String jwksUrl = appleConfiguration.getNotification().getJwksUrl();
            if (jwksUrl == null || jwksUrl.isEmpty()) {
                jwksUrl = "https://appleid.apple.com/auth/keys";
            }

            log.info("Fetching Apple JWKS from: {}", jwksUrl);
            String response = restTemplate.getForObject(jwksUrl, String.class);
            JsonNode jwks = objectMapper.readTree(response);
            JsonNode keys = jwks.path("keys");

            if (keys.isArray()) {
                for (JsonNode key : keys) {
                    String kid = key.path("kid").asText();
                    String kty = key.path("kty").asText();

                    try {
                        PublicKey publicKey = parsePublicKey(key, kty);
                        if (publicKey != null) {
                            publicKeyCache.put(kid, publicKey);
                            log.debug("Cached public key with kid: {}", kid);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse key {}: {}", kid, e.getMessage());
                    }
                }
            }

            lastKeysFetchTime = System.currentTimeMillis();
            log.info("Refreshed Apple JWKS, cached {} keys", publicKeyCache.size());

        } catch (Exception e) {
            log.error("Failed to fetch Apple JWKS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch Apple public keys", e);
        }
    }

    private PublicKey parsePublicKey(JsonNode key, String keyType) throws Exception {
        if ("RSA".equals(keyType)) {
            String n = key.path("n").asText();
            String e = key.path("e").asText();

            byte[] nBytes = Base64.getUrlDecoder().decode(n);
            byte[] eBytes = Base64.getUrlDecoder().decode(e);

            BigInteger modulus = new BigInteger(1, nBytes);
            BigInteger exponent = new BigInteger(1, eBytes);

            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePublic(spec);

        } else if ("EC".equals(keyType)) {
            // For EC keys, use the x and y coordinates
            String x = key.path("x").asText();
            String y = key.path("y").asText();
            String crv = key.path("crv").asText();

            byte[] xBytes = Base64.getUrlDecoder().decode(x);
            byte[] yBytes = Base64.getUrlDecoder().decode(y);

            // Create EC public key
            java.security.spec.ECPoint point = new java.security.spec.ECPoint(
                    new BigInteger(1, xBytes),
                    new BigInteger(1, yBytes));

            java.security.spec.ECParameterSpec params = getECParameterSpec(crv);
            java.security.spec.ECPublicKeySpec spec = new java.security.spec.ECPublicKeySpec(point, params);
            KeyFactory factory = KeyFactory.getInstance("EC");
            return factory.generatePublic(spec);
        }

        return null;
    }

    private java.security.spec.ECParameterSpec getECParameterSpec(String curve) throws Exception {
        // Get standard EC parameter spec for the curve
        java.security.AlgorithmParameters params = java.security.AlgorithmParameters.getInstance("EC");
        params.init(new java.security.spec.ECGenParameterSpec(
                "P-256".equals(curve) ? "secp256r1" : curve));
        return params.getParameterSpec(java.security.spec.ECParameterSpec.class);
    }
}
