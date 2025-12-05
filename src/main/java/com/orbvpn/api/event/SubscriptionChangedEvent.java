package com.orbvpn.api.event;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event that is published when a subscription status changes.
 * This includes creation, renewal, cancellation, and other subscription-related
 * events.
 */
@Getter
public class SubscriptionChangedEvent extends ApplicationEvent {
    private final User user;
    private final UserSubscription subscription;
    private final String eventType;
    private final String password;

    /**
     * Creates a new subscription event without password information.
     *
     * @param source       The source of the event
     * @param user         The user whose subscription changed
     * @param subscription The subscription that changed
     * @param eventType    The type of change (e.g., "NEW_SUBSCRIPTION",
     *                     "SUBSCRIPTION_RENEWED")
     */
    public SubscriptionChangedEvent(Object source, User user, UserSubscription subscription, String eventType) {
        this(source, user, subscription, eventType, null);
    }

    /**
     * Creates a new subscription event with password information.
     *
     * @param source       The source of the event
     * @param user         The user whose subscription changed
     * @param subscription The subscription that changed
     * @param eventType    The type of change (e.g., "NEW_SUBSCRIPTION",
     *                     "SUBSCRIPTION_RENEWED")
     * @param password     Optional password information (can be null)
     */
    public SubscriptionChangedEvent(Object source, User user, UserSubscription subscription, String eventType,
            String password) {
        super(source);
        this.user = user;
        this.subscription = subscription;
        this.eventType = eventType;
        this.password = password;
    }

    /**
     * Checks if this event includes password information.
     *
     * @return true if password is present, false otherwise
     */
    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }
}