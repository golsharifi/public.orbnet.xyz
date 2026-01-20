package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.MigrationResult;
import com.orbvpn.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserUuidMigrationService {
    private final UserRepository userRepository;
    private static final int MAX_USERS_TO_PROCESS = 10;

    @Value("${migration.delay-between-users:100}")
    private long delayBetweenUsers;

    @Retryable(retryFor = { DataAccessException.class }, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void migrateExistingUsers() {
        try {
            log.info("=== STARTING UUID MIGRATION PROCESS (Limited to {} users) ===", MAX_USERS_TO_PROCESS);
            log.info("Checking for users without UUID...");

            long usersWithoutUuidCount = userRepository.countByUuidIsNull();
            long usersToProcess = Math.min(usersWithoutUuidCount, MAX_USERS_TO_PROCESS);

            log.info("Found {} users without UUID in total", usersWithoutUuidCount);
            log.info("Will process next {} users in this run", usersToProcess);

            if (usersWithoutUuidCount == 0) {
                log.info("No users need UUID migration. Process complete.");
                return;
            }

            int processedCount = 0;
            int failedCount = 0;
            boolean hasMore = true;

            while (hasMore && processedCount < MAX_USERS_TO_PROCESS) {
                try {
                    log.debug("Fetching next user without UUID... ({} of {})",
                            processedCount + 1, MAX_USERS_TO_PROCESS);

                    // Get list of user IDs without UUID - using simple List<Integer>
                    List<Integer> userIds = userRepository.findFirstUserIdWithoutUuid();

                    if (userIds.isEmpty()) {
                        log.info("No more users to process");
                        hasMore = false;
                        continue;
                    }

                    Integer userId = userIds.get(0);
                    String userEmail = userRepository.findEmailById(userId);

                    log.info("Processing user ID: {} (email: {}) - User {} of {}",
                            userId, userEmail, processedCount + 1, MAX_USERS_TO_PROCESS);

                    boolean success = processAndVerifyUser(userId);

                    if (success) {
                        processedCount++;
                        log.info("Progress: {}/{} users processed - Successfully processed user ID: {} ({})",
                                processedCount,
                                usersToProcess,
                                userId,
                                userEmail);
                    } else {
                        failedCount++;
                        log.error("Failed to process user ID: {} (email: {}) - Attempt will be made again in next run",
                                userId, userEmail);
                    }

                    if (processedCount >= MAX_USERS_TO_PROCESS) {
                        log.info("Reached maximum number of users to process ({})", MAX_USERS_TO_PROCESS);
                        break;
                    }

                    if (delayBetweenUsers > 0) {
                        log.debug("Waiting {} ms before processing next user...", delayBetweenUsers);
                        Thread.sleep(delayBetweenUsers);
                    }

                } catch (InterruptedException e) {
                    log.error("Migration process was interrupted: {}", e.getMessage());
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    failedCount++;
                    log.error("Error processing user: {}", e.getMessage(), e);
                }
            }

            log.info("=== UUID MIGRATION COMPLETED (Limited Run) ===");
            log.info("Total users processed successfully: {}", processedCount);
            log.info("Total users failed: {}", failedCount);
            log.info("Maximum users allowed in this run: {}", MAX_USERS_TO_PROCESS);

            // Final verification
            long remainingUsersWithoutUuid = userRepository.countByUuidIsNull();
            log.info("Remaining users without UUID: {}", remainingUsersWithoutUuid);
            log.info("To process more users, run the migration again.");

        } catch (Exception e) {
            log.error("Critical error during migration process: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processAndVerifyUser(int userId) {
        try {
            log.debug("Starting to process user ID: {}", userId);

            // Check if user exists and get current UUID - using simple String queries
            String currentUuid = userRepository.findUuidById(userId);
            String userEmail = userRepository.findEmailById(userId);

            if (userEmail == null) {
                log.error("User with ID: {} not found", userId);
                return false;
            }

            log.debug("Retrieved user email: {}", userEmail);

            if (currentUuid != null && !currentUuid.isEmpty()) {
                log.info("User ID: {} already has UUID: {} - Skipping", userId, currentUuid);
                return false;
            }

            String uuid = UUID.randomUUID().toString();
            log.debug("Generated UUID {} for user ID: {}", uuid, userId);

            // Update UUID using raw query
            int updatedRows = userRepository.updateUuidById(userId, uuid);

            if (updatedRows != 1) {
                log.error("Failed to update UUID for user ID: {} - Updated {} rows", userId, updatedRows);
                return false;
            }

            userRepository.flush();

            // Verify the save
            log.debug("Verifying UUID save in database...");
            String verifiedUuid = userRepository.findUuidById(userId);

            if (!uuid.equals(verifiedUuid)) {
                log.error("UUID mismatch for user {}. Expected: {}, Found: {}",
                        userId, uuid, verifiedUuid);
                return false;
            }

            log.info("Successfully generated and verified UUID {} for user {} (email: {})",
                    uuid, userId, userEmail);
            return true;

        } catch (Exception e) {
            log.error("Failed to process user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    @Transactional(readOnly = true)
    public long countUsersWithoutUuid() {
        return userRepository.countByUuidIsNull();
    }

    @Transactional(readOnly = true)
    public MigrationResult validateUuids() {
        log.info("=== STARTING UUID VALIDATION ===");
        MigrationResult result = new MigrationResult(0, 0, 0, 0);

        try {
            log.info("Counting total users...");
            long total = userRepository.count();
            result.setTotalUsers((int) total);
            log.info("Total users found: {}", total);

            long missingUuids = userRepository.countByUuidIsNull();
            result.setMissingUuids((int) missingUuids);

            log.info("Users without UUID: {}", missingUuids);

        } catch (Exception e) {
            log.error("Error during UUID validation: {}", e.getMessage(), e);
        }

        log.info("=== UUID VALIDATION COMPLETED ===");
        log.info("Total Users: {}", result.getTotalUsers());
        log.info("Missing UUIDs: {}", result.getMissingUuids());

        return result;
    }
}