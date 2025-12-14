package com.orbvpn.api.service.payment.coinpayment;

public class Constants {

    public static final String HMAC_SHA_512 = "HmacSHA512";

    public static final String COINS_API_URL = "https://www.coinpayments.net/api.php";
    public final static String SUCCESS_MESSAGE = "ok";
    public final static String VERIFY_TEMPLATE = "verify.ftlh";
    public final static String CONFIRM_EMAIL_CHANGE_TEMPLATE = "confirmEmailChange.ftlh";
    public final static String _2FA_TEMPLATE = "2faEmail.ftlh";
    public final static String RESET_TEMPLATE = "reset.ftlh";
    public final static String NEW_USER_CREATED = "new_user.ftlh";

    // CoinPayments IPN Status Codes
    // See: https://www.coinpayments.net/merchant-tools-ipn

    // Negative statuses indicate payment failure/error
    public static final int STATUS_CANCELLED = -1;        // Cancelled / Timed out
    public static final int STATUS_ERROR = -2;            // Unknown error

    // Zero and positive statuses
    public static final int STATUS_WAITING = 0;           // Waiting for funds
    public static final int STATUS_CONFIRMING = 1;        // Funds received, confirming
    public static final int STATUS_COMPLETE = 2;          // Payment complete (less than 100)
    public static final int STATUS_QUEUED = 3;            // Payment is queued for payout

    // Status >= 100 means payment is complete and confirmed
    public static final int STATUS_COMPLETE_THRESHOLD = 100;

    /**
     * Checks if the IPN status indicates a completed payment
     * Status >= 100 OR status == 2 means payment is complete
     */
    public static boolean isPaymentComplete(int status) {
        return status >= STATUS_COMPLETE_THRESHOLD || status == STATUS_COMPLETE;
    }

    /**
     * Checks if the IPN status indicates a failed payment
     * Negative status values indicate failures
     */
    public static boolean isPaymentFailed(int status) {
        return status < 0;
    }

    /**
     * Checks if the IPN status indicates payment is still pending
     */
    public static boolean isPaymentPending(int status) {
        return status >= 0 && status < STATUS_COMPLETE && status < STATUS_COMPLETE_THRESHOLD;
    }
}
