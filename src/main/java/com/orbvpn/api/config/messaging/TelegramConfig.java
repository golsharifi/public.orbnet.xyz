package com.orbvpn.api.config.messaging;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class TelegramConfig {
    @Value("${messaging.telegram.bot-token}")
    private String botToken;

    @Value("${messaging.telegram.bot-username}")
    private String botUsername;

    @Value("${messaging.telegram.admin-group-id}")
    private String adminGroupId;
}