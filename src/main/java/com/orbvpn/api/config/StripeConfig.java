package com.orbvpn.api.config;

import com.stripe.Stripe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class StripeConfig {
    @Value("${stripe.api.secret-key}")
    private String secretKey;

    @Value("${stripe.api.public-key}")
    private String publicKey;

    @Value("${stripe.api.webhook-secret}")
    private String webhookSecret;

    @Bean
    public RestTemplate stripeRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public String stripeWebhookSecret() {
        return webhookSecret;
    }
}