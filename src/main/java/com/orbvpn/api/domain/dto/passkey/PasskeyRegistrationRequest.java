package com.orbvpn.api.domain.dto.passkey;

import lombok.Data;

import java.util.List;

@Data
public class PasskeyRegistrationRequest {
    private String id;
    private String rawId;
    private String type;
    private AuthenticatorResponse response;
    private String authenticatorAttachment;
    private ClientExtensionResults clientExtensionResults;
    private String passkeyName;

    @Data
    public static class AuthenticatorResponse {
        private String clientDataJSON;
        private String attestationObject;
        private List<String> transports;
    }

    @Data
    public static class ClientExtensionResults {
        private Boolean credProps;
    }
}
