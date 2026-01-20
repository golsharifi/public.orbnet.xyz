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
public class PasskeyAuthenticationOptions {
    private String challenge;
    private Long timeout;
    private String rpId;
    private List<AllowCredential> allowCredentials;
    private String userVerification;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllowCredential {
        private String id;
        private String type;
        private List<String> transports;
    }
}
