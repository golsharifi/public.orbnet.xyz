package com.orbvpn.api.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.time.LocalDateTime;

@Getter
public class UsageEvent extends ApplicationEvent {
    private final Long userId;
    private final String eventType;
    private final Long usage;
    private final Long limit;
    private final String metricType;
    private final String period;
    private final LocalDateTime eventDateTime;

    public UsageEvent(Object source, Long userId, String eventType, Long usage, Long limit,
            String metricType, String period) {
        super(source);
        this.userId = userId;
        this.eventType = eventType;
        this.usage = usage;
        this.limit = limit;
        this.metricType = metricType;
        this.period = period;
        this.eventDateTime = LocalDateTime.now();
    }

    public double getUsagePercentage() {
        return (usage.doubleValue() / limit.doubleValue()) * 100;
    }

    public boolean isLimitExceeded() {
        return usage >= limit;
    }
}
