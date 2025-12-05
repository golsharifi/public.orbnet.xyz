package com.orbvpn.api.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Configuration
public class PaymentConfig {

    @Bean
    public HttpTransport httpTransport() throws GeneralSecurityException, IOException {
        return GoogleNetHttpTransport.newTrustedTransport();
    }

    @Bean
    public AndroidPublisher.Builder publisherBuilder(HttpTransport httpTransport) {
        return new AndroidPublisher.Builder(
                httpTransport,
                GsonFactory.getDefaultInstance(),
                null)
                .setApplicationName("OrbVPN");
    }
}