package com.orbvpn.api.config.scheduler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SchedulerConfig {

    @Bean
    public SchedulerManager schedulerManager() {
        return new SchedulerManager();
    }
}