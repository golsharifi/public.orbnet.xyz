package com.orbvpn.api.service.subscription;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;

/**
 * Interface for providing subscription-related information.
 * This interface helps break circular dependencies and provides
 * a clear contract for subscription-related operations.
 */
public interface SubscriptionProvider {
    /**
     * Retrieves the current subscription for a given user.
     *
     * @param user The user whose subscription should be retrieved
     * @return The current UserSubscription if it exists, null otherwise
     * @throws IllegalArgumentException if user is null
     */
    UserSubscription getCurrentSubscription(User user);

    /**
     * Checks if a user has a valid subscription.
     *
     * @param user The user to check
     * @return true if the user has a valid subscription, false otherwise
     */
    default boolean hasValidSubscription(User user) {
        UserSubscription subscription = getCurrentSubscription(user);
        return subscription != null && !subscription.isCanceled();
    }

    /**
     * Validates if a user object is properly initialized.
     *
     * @param user The user to validate
     * @throws IllegalArgumentException if user is null
     */
    default void validateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
    }
}
