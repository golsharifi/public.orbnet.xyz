package com.orbvpn.api.domain.dto;

import lombok.Data;

@Data
public class TelegramRegistration {
    private String username;
    private String verificationCode;
}