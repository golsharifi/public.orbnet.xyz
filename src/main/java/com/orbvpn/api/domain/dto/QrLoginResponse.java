package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrLoginResponse {
    private String qrCodeImage;
    private String sessionId;
    private String qrCodeValue;
    private String deepLinkUrl;
    private LocalDateTime expiresAt;
    private String status;
}