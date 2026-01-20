package com.orbvpn.api.config.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SchedulerManager {
    private volatile ScheduledExecutorService scheduler;

    public synchronized ScheduledExecutorService getScheduler() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(4);
            log.info("Created new scheduler");
        }
        return scheduler;
    }

    public synchronized void shutdownScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            try {
                scheduler.shutdown();
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                log.info("Scheduler shutdown completed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
                log.error("Scheduler shutdown interrupted", e);
            }
        }
    }
}