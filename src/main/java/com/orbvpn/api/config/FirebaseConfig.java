package com.orbvpn.api.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
@Configuration
public class FirebaseConfig {

    private static final String APP_NAME = "OrbVPN";

    @Bean
    public FirebaseMessaging firebaseMessaging() throws IOException {
        try {
            GoogleCredentials googleCredentials = GoogleCredentials
                    .fromStream(new ClassPathResource("firebase-service-account.json").getInputStream());

            FirebaseOptions firebaseOptions = FirebaseOptions
                    .builder()
                    .setCredentials(googleCredentials)
                    .build();

            FirebaseApp app = getFirebaseAppByName(APP_NAME);
            if (app == null) {
                app = FirebaseApp.initializeApp(firebaseOptions, APP_NAME);
                log.info("Initialized new Firebase application: {}", APP_NAME);
            } else {
                log.info("Using existing Firebase application: {}", APP_NAME);
            }

            return FirebaseMessaging.getInstance(app);
        } catch (IOException e) {
            log.error("Failed to initialize Firebase", e);
            throw e;
        }
    }

    private FirebaseApp getFirebaseAppByName(String name) {
        try {
            return FirebaseApp.getApps().stream()
                    .filter(app -> app.getName().equals(name))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Error checking for existing Firebase app", e);
            return null;
        }
    }
}