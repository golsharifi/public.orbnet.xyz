package com.orbvpn.api.domain.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity for tracking all admin actions for audit purposes.
 * Records who did what, to whom, when, and from where.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "admin_audit_logs", indexes = {
    @Index(name = "idx_audit_admin_id", columnList = "admin_id"),
    @Index(name = "idx_audit_action_type", columnList = "action_type"),
    @Index(name = "idx_audit_target_type", columnList = "target_type"),
    @Index(name = "idx_audit_target_id", columnList = "target_id"),
    @Index(name = "idx_audit_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The admin user who performed the action
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    /**
     * Admin's email for quick reference (denormalized for performance)
     */
    @Column(name = "admin_email", length = 255, nullable = false)
    private String adminEmail;

    /**
     * Type of action performed
     */
    @Column(name = "action_type", length = 50, nullable = false)
    private String actionType;

    /**
     * Type of entity being acted upon (USER, SUBSCRIPTION, SERVER, GROUP, etc.)
     */
    @Column(name = "target_type", length = 50, nullable = false)
    private String targetType;

    /**
     * ID of the target entity
     */
    @Column(name = "target_id", length = 100)
    private String targetId;

    /**
     * Human-readable identifier (e.g., user email, server name)
     */
    @Column(name = "target_identifier", length = 255)
    private String targetIdentifier;

    /**
     * JSON representation of the entity state before the action (for updates/deletes)
     */
    @Column(name = "before_value", columnDefinition = "TEXT")
    private String beforeValue;

    /**
     * JSON representation of the entity state after the action (for creates/updates)
     */
    @Column(name = "after_value", columnDefinition = "TEXT")
    private String afterValue;

    /**
     * Additional details or notes about the action
     */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * IP address of the admin when performing the action
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User agent string from the request
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Request ID for tracing (if available)
     */
    @Column(name = "request_id", length = 100)
    private String requestId;

    /**
     * Whether the action was successful
     */
    @Column(name = "success", nullable = false)
    @Builder.Default
    private boolean success = true;

    /**
     * Error message if the action failed
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    // Common action types as constants
    public static final String ACTION_CREATE_USER = "CREATE_USER";
    public static final String ACTION_UPDATE_USER = "UPDATE_USER";
    public static final String ACTION_DELETE_USER = "DELETE_USER";
    public static final String ACTION_TOGGLE_USER_ENABLED = "TOGGLE_USER_ENABLED";
    public static final String ACTION_ENABLE_USER = "ENABLE_USER";
    public static final String ACTION_DISABLE_USER = "DISABLE_USER";
    public static final String ACTION_RESET_USER_PASSWORD = "RESET_USER_PASSWORD";

    public static final String ACTION_RENEW_SUBSCRIPTION = "RENEW_SUBSCRIPTION";
    public static final String ACTION_RESET_SUBSCRIPTION = "RESET_SUBSCRIPTION";
    public static final String ACTION_CHANGE_PLAN = "CHANGE_PLAN";
    public static final String ACTION_UPDATE_SUBSCRIPTION = "UPDATE_SUBSCRIPTION";
    public static final String ACTION_ADD_DAYS = "ADD_DAYS";

    public static final String ACTION_ADD_DEVICES = "ADD_DEVICES";
    public static final String ACTION_SET_DEVICES = "SET_DEVICES";
    public static final String ACTION_DELETE_DEVICES = "DELETE_DEVICES";

    public static final String ACTION_ADD_RESELLER_CREDIT = "ADD_RESELLER_CREDIT";
    public static final String ACTION_DEDUCT_RESELLER_CREDIT = "DEDUCT_RESELLER_CREDIT";

    public static final String ACTION_CREATE_SERVER = "CREATE_SERVER";
    public static final String ACTION_UPDATE_SERVER = "UPDATE_SERVER";
    public static final String ACTION_DELETE_SERVER = "DELETE_SERVER";

    public static final String ACTION_CREATE_GROUP = "CREATE_GROUP";
    public static final String ACTION_UPDATE_GROUP = "UPDATE_GROUP";
    public static final String ACTION_DELETE_GROUP = "DELETE_GROUP";

    public static final String ACTION_CREATE_GIFT_CARD = "CREATE_GIFT_CARD";
    public static final String ACTION_CANCEL_GIFT_CARD = "CANCEL_GIFT_CARD";

    public static final String ACTION_LOGIN = "LOGIN";
    public static final String ACTION_LOGOUT = "LOGOUT";

    // Target type constants
    public static final String TARGET_USER = "USER";
    public static final String TARGET_SUBSCRIPTION = "SUBSCRIPTION";
    public static final String TARGET_SERVER = "SERVER";
    public static final String TARGET_GROUP = "GROUP";
    public static final String TARGET_RESELLER = "RESELLER";
    public static final String TARGET_GIFT_CARD = "GIFT_CARD";
    public static final String TARGET_SERVICE_GROUP = "SERVICE_GROUP";
}
