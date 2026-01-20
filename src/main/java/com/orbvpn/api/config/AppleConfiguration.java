package com.orbvpn.api.config;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "apple")
@Getter
@Setter
public class AppleConfiguration {
    private String keyId;
    private String teamId;
    private String bundleId;
    private String environment;
    private String sharedSecret;

    @NestedConfigurationProperty
    private Verification verification = new Verification();

    @NestedConfigurationProperty
    private Notification notification = new Notification();

    @Getter
    @Setter
    public static class Verification {
        private String url;
        private String sandboxUrl;
        private boolean enabled = true;
        private boolean metricsEnabled = true;
    }

    @Getter
    @Setter
    public static class Notification {
        private String jwksUrl;

        @NestedConfigurationProperty
        private Timeout timeout = new Timeout();

        @NestedConfigurationProperty
        private Retry retry = new Retry();

        @NestedConfigurationProperty
        private Verification verification = new Verification();

        @Getter
        @Setter
        public static class Timeout {
            private int connect = 5000;
            private int read = 5000;
        }

        @Getter
        @Setter
        public static class Retry {
            private int maxAttempts = 3;
            private long delay = 1000;
        }

        @Getter
        @Setter
        public static class Verification {
            private boolean enabled = true;
            private boolean metricsEnabled = true;
        }
    }

    private Map<String, AppStoreProduct> products = new HashMap<>();

    @Getter
    @Setter
    public static class AppStoreProduct {
        private BigDecimal price;
        private String currency;
    }
}
