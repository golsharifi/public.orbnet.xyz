package com.orbvpn.api.config.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "notification")
@Data
public class NotificationConfig {
    private DefaultChannels defaultChannels = new DefaultChannels();

    @Data
    public static class DefaultChannels {
        private boolean emailEnabled = true;
        private boolean whatsappEnabled = true;
        private boolean telegramEnabled = true;
        private boolean smsEnabled = false;
    }
}