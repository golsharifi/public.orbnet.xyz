package com.orbvpn.api.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Configuration
public class GooglePlayConfig {

    @Value("${google.play.credentials.path}")
    private Resource serviceAccountKey;

    @Value("${app.package.name}")
    private String applicationName;

    @Bean
    public AndroidPublisher androidPublisher() throws GeneralSecurityException, IOException {
        if (serviceAccountKey == null || !serviceAccountKey.exists()) {
            throw new IllegalStateException(
                    "Google Play service account key file not found at: " +
                            (serviceAccountKey != null ? serviceAccountKey.getDescription() : "null"));
        }

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        // Load service account credentials
        GoogleCredentials credentials;
        try {
            credentials = GoogleCredentials
                    .fromStream(serviceAccountKey.getInputStream())
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/androidpublisher"));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Google Play service account credentials", e);
        }

        // Create AndroidPublisher service
        return new AndroidPublisher.Builder(
                httpTransport,
                jsonFactory,
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(StringUtils.hasText(applicationName) ? applicationName : "OrbVPN")
                .build();
    }

    @Bean
    public AndroidPublisher.Builder androidPublisherBuilder(AndroidPublisher publisher) {
        return new AndroidPublisher.Builder(
                publisher.getRequestFactory().getTransport(),
                publisher.getJsonFactory(),
                publisher.getRequestFactory().getInitializer());
    }
}