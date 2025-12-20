package com.orbvpn.api.domain.dto.passkey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyView {
    private Long id;
    private String credentialId;
    private String name;
    private String deviceType;
    private Boolean backedUp;
    private List<String> transports;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}
