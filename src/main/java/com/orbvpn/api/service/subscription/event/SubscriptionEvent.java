package com.orbvpn.api.service.subscription.event;

import com.orbvpn.api.domain.entity.UserSubscription;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubscriptionEvent extends ApplicationEvent {
    private final UserSubscription subscription;
    private final String eventType;

    public SubscriptionEvent(Object source, UserSubscription subscription, String eventType) {
        super(source);
        this.subscription = subscription;
        this.eventType = eventType;
    }
}
