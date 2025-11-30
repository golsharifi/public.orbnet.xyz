package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDeviceDto {
    private String os;

    @NotBlank(message = "Device ID is required")
    private String deviceId;

    private String appVersion;
    private String deviceName;
    private String deviceModel;
    private String fcmToken;
}
