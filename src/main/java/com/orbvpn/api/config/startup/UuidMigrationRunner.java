package com.orbvpn.api.config.startup;

import com.orbvpn.api.service.UserUuidMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
@ConditionalOnProperty(name = "uuid.migration.enabled", havingValue = "true", matchIfMissing = false)
public class UuidMigrationRunner implements CommandLineRunner {
    private final UserUuidMigrationService migrationService;
    private final Environment environment;
    private static final int MAX_RETRIES = 1000; // Safety limit
    private static final long BATCH_DELAY = 5000; // 5 seconds between batches

    @Override
    public void run(String... args) {
        // Skip in test environment
        if (environment.acceptsProfiles(Profiles.of("test"))) {
            log.info("Skipping UUID migration in test environment");
            return;
        }

        log.info("=== STARTING UUID MIGRATION RUNNER ===");
        processBatchesUntilComplete();
        log.info("=== UUID MIGRATION RUNNER COMPLETED ===");
    }

    private void processBatchesUntilComplete() {
        int batchCount = 0;
        boolean hasMoreUsers = true;

        while (hasMoreUsers && batchCount < MAX_RETRIES) {
            try {
                log.info("Starting batch #{}", batchCount + 1);

                // Check if there are any users left to process
                long remainingUsers = migrationService.countUsersWithoutUuid();

                if (remainingUsers == 0) {
                    log.info("No more users need UUID migration. Process complete.");
                    hasMoreUsers = false;
                    break;
                }

                // Process the next batch
                migrationService.migrateExistingUsers();
                batchCount++;

                // Verify if there are still users to process
                remainingUsers = migrationService.countUsersWithoutUuid();
                log.info("Batch #{} completed. Remaining users: {}", batchCount, remainingUsers);

                if (remainingUsers == 0) {
                    log.info("All users have been processed. Migration complete.");
                    hasMoreUsers = false;
                } else {
                    log.info("Waiting {} ms before starting next batch...", BATCH_DELAY);
                    Thread.sleep(BATCH_DELAY);
                }

            } catch (InterruptedException e) {
                log.error("Migration process was interrupted: {}", e.getMessage());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing batch #{}: {}", batchCount + 1, e.getMessage(), e);
                log.info("Waiting {} ms before retrying...", BATCH_DELAY);
                try {
                    Thread.sleep(BATCH_DELAY);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (batchCount >= MAX_RETRIES) {
            log.warn("Reached maximum number of batches ({}). Stopping migration process.", MAX_RETRIES);
        }

        log.info("Migration process finished after {} batches", batchCount);
    }
}
