package com.orbvpn.api.domain.dto.passkey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyRegistrationOptions {
    private String challenge;
    private RelyingParty rp;
    private UserInfo user;
    private List<PubKeyCredParam> pubKeyCredParams;
    private Long timeout;
    private String attestation;
    private AuthenticatorSelection authenticatorSelection;
    private List<ExcludeCredential> excludeCredentials;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelyingParty {
        private String id;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String id;
        private String name;
        private String displayName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PubKeyCredParam {
        private String type;
        private Integer alg;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthenticatorSelection {
        private String authenticatorAttachment;
        private Boolean requireResidentKey;
        private String residentKey;
        private String userVerification;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExcludeCredential {
        private String id;
        private String type;
        private List<String> transports;
    }
}
