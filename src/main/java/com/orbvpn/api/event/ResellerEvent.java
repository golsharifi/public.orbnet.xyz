package com.orbvpn.api.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class ResellerEvent extends ApplicationEvent {
    private final Long resellerId;
    private final String eventType;
    private final String action;
    private final BigDecimal amount;
    private final String currency;
    private final String notes;
    private final LocalDateTime eventDateTime;

    public ResellerEvent(Object source, Long resellerId, String eventType, String action,
            BigDecimal amount, String currency, String notes) {
        super(source);
        this.resellerId = resellerId;
        this.eventType = eventType;
        this.action = action;
        this.amount = amount;
        this.currency = currency;
        this.notes = notes;
        this.eventDateTime = LocalDateTime.now();
    }
}
