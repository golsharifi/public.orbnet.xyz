package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserUuidService {
    private final UserRepository userRepository;

    /**
     * Get the UUID for a user, creating one if it doesn't exist
     */
    @Transactional(readOnly = true)
    public String getUuid(User user) {
        if (user == null) {
            log.warn("Attempted to get UUID for null user");
            return null;
        }

        if (user.getUuid() != null && isValidUuid(user.getUuid())) {
            return user.getUuid();
        }

        return getOrCreateUuid(user.getId());
    }

    /**
     * Get or create a UUID for a user by ID
     */
    @Retryable(retryFor = { DataAccessException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
    public String getOrCreateUuid(Integer userId) {
        try {
            log.debug("Getting or creating UUID for user ID: {}", userId);

            // First check if user exists
            if (!userRepository.existsById(userId)) {
                log.error("User with ID {} does not exist", userId);
                throw new IllegalArgumentException("User not found with ID: " + userId);
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

            // If user already has a valid UUID, return it
            if (user.getUuid() != null && isValidUuid(user.getUuid())) {
                log.debug("User {} already has UUID: {}", userId, user.getUuid());
                return user.getUuid();
            }

            // Generate new UUID
            String uuid = UUID.randomUUID().toString();
            log.debug("Generated new UUID {} for user {}", uuid, userId);

            // Save the UUID
            user.setUuid(uuid);
            userRepository.save(user);
            userRepository.flush();

            // Verify the UUID was saved correctly
            User verifiedUser = userRepository.findById(userId).orElse(null);
            if (verifiedUser == null || !uuid.equals(verifiedUser.getUuid())) {
                log.error("Failed to verify UUID for user {}", userId);
                throw new IllegalStateException("UUID verification failed for user: " + userId);
            }

            log.info("Successfully created and verified UUID {} for user {}", uuid, userId);
            return uuid;
        } catch (IllegalArgumentException e) {
            // Re-throw argument exceptions (like user not found)
            throw e;
        } catch (Exception e) {
            log.error("Error creating/retrieving UUID for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to create UUID for user: " + userId, e);
        }
    }

    /**
     * Ensure a user has a valid UUID
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void ensureUuidExists(User user) {
        if (user == null) {
            log.warn("Attempted to ensure UUID for null user");
            return;
        }

        try {
            if (user.getUuid() == null || !isValidUuid(user.getUuid())) {
                getOrCreateUuid(user.getId());
                log.info("Ensured UUID exists for user {}", user.getId());
            }
        } catch (Exception e) {
            log.error("Failed to ensure UUID for user {}: {}", user.getId(), e.getMessage(), e);
        }
    }

    /**
     * Check if a UUID string is valid
     */
    private boolean isValidUuid(String uuid) {
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}