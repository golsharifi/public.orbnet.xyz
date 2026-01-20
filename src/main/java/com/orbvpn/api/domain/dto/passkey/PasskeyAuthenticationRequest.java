package com.orbvpn.api.domain.dto.passkey;

import lombok.Data;

@Data
public class PasskeyAuthenticationRequest {
    private String id;
    private String rawId;
    private String type;
    private AuthenticatorAssertionResponse response;
    private String authenticatorAttachment;
    private ClientExtensionResults clientExtensionResults;

    @Data
    public static class AuthenticatorAssertionResponse {
        private String clientDataJSON;
        private String authenticatorData;
        private String signature;
        private String userHandle;
    }

    @Data
    public static class ClientExtensionResults {
    }
}
