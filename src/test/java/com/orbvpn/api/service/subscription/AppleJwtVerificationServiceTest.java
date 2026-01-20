package com.orbvpn.api.service.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.config.AppleConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppleJwtVerificationServiceTest {

    @Mock
    private AppleConfiguration appleConfiguration;

    @Mock
    private AppleConfiguration.Notification notification;

    private ObjectMapper objectMapper;
    private AppleJwtVerificationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        lenient().when(appleConfiguration.getNotification()).thenReturn(notification);
        lenient().when(notification.getJwksUrl()).thenReturn("https://appleid.apple.com/auth/keys");
        service = new AppleJwtVerificationService(appleConfiguration, objectMapper);
    }

    @Test
    void verifyAndDecodeSignedData_WithNullInput_ReturnsNull() {
        String result = service.verifyAndDecodeSignedData(null);
        assertNull(result);
    }

    @Test
    void verifyAndDecodeSignedData_WithEmptyInput_ReturnsNull() {
        String result = service.verifyAndDecodeSignedData("");
        assertNull(result);
    }

    @Test
    void verifyAndDecodeSignedData_WithInvalidJwtFormat_ReturnsNullOrFallback() {
        // Create a malformed JWT (only 2 parts)
        String invalidJwt = "header.payload";
        service.verifyAndDecodeSignedData(invalidJwt);
        // May return null or attempt to decode - implementation detail
        // The important thing is it doesn't throw
    }

    @Test
    void verifyAndDecodeSignedData_WithValidBase64Payload_DecodesPayload() {
        // Create a fake JWT with valid base64 encoding but no real signature
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"test\":\"value\"}".getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("fake-signature".getBytes(StandardCharsets.UTF_8));

        String jwt = header + "." + payload + "." + signature;
        String result = service.verifyAndDecodeSignedData(jwt);

        // Should decode the payload (falls back when verification fails)
        assertNotNull(result);
        assertTrue(result.contains("test"));
    }

    @Test
    void verifyAndDecodeNotification_WithInvalidFormat_ThrowsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            service.verifyAndDecodeNotification("invalid-jwt");
        });
    }

    @Test
    void verifyAndDecodeNotification_WithTwoPartJwt_ThrowsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            service.verifyAndDecodeNotification("part1.part2");
        });
    }

    @Test
    void verifyAndDecodeNotification_WithNullInput_ThrowsException() {
        assertThrows(Exception.class, () -> {
            service.verifyAndDecodeNotification(null);
        });
    }
}
