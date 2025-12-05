package com.orbvpn.api.exception;

/**
 * Exception thrown when a user exceeds their ad watching limits (hourly or daily).
 * This provides better error handling than generic RuntimeException.
 */
public class AdLimitExceededException extends RuntimeException {

    private final LimitType limitType;
    private final int currentCount;
    private final int maxLimit;

    public AdLimitExceededException(String message) {
        super(message);
        this.limitType = LimitType.UNKNOWN;
        this.currentCount = 0;
        this.maxLimit = 0;
    }

    public AdLimitExceededException(LimitType limitType, int currentCount, int maxLimit) {
        super(String.format("%s ad limit exceeded: %d/%d", limitType.getDisplayName(), currentCount, maxLimit));
        this.limitType = limitType;
        this.currentCount = currentCount;
        this.maxLimit = maxLimit;
    }

    public LimitType getLimitType() {
        return limitType;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public int getMaxLimit() {
        return maxLimit;
    }

    public enum LimitType {
        HOURLY("Hourly"),
        DAILY("Daily"),
        UNKNOWN("Unknown");

        private final String displayName;

        LimitType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
