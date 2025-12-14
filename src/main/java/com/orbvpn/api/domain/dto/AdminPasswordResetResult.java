package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO returned to admin/reseller after resetting a user's password.
 * Contains the new password so the admin can communicate it to the user if needed.
 * The password is also sent to the user via configured notification channels.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPasswordResetResult {

    /**
     * The user's ID
     */
    private Integer userId;

    /**
     * The user's email address
     */
    private String email;

    /**
     * The user's username
     */
    private String username;

    /**
     * The newly generated password (plaintext, for admin to see)
     * This password has already been set on the user's account and
     * notifications have been sent to the user.
     */
    private String newPassword;

    /**
     * Indicates whether the user was notified via email
     */
    private boolean emailNotificationSent;

    /**
     * Indicates whether the user was notified via other channels (SMS, WhatsApp, etc.)
     */
    private boolean otherNotificationsSent;

    /**
     * Brief message about the operation result
     */
    private String message;
}
