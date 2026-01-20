package com.orbvpn.api.domain.dto;

import lombok.Data;

@Data
public class ChatMessage {
    private String recipient;
    private String message;
}