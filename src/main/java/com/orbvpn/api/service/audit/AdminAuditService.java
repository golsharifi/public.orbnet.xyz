package com.orbvpn.api.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbvpn.api.domain.entity.AdminAuditLog;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for creating and querying admin audit logs.
 * Provides methods to log admin actions and retrieve audit history.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditService {

    private final AdminAuditLogRepository auditLogRepository;

    /**
     * Log an admin action with full details.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminAuditLog logAction(
            String actionType,
            String targetType,
            String targetId,
            String targetIdentifier,
            Object beforeValue,
            Object afterValue,
            String description) {

        try {
            User admin = getCurrentAdmin();
            if (admin == null) {
                log.warn("Cannot log audit action - no admin user in context");
                return null;
            }

            AdminAuditLog auditLog = AdminAuditLog.builder()
                    .admin(admin)
                    .adminEmail(admin.getEmail())
                    .actionType(actionType)
                    .targetType(targetType)
                    .targetId(targetId)
                    .targetIdentifier(targetIdentifier)
                    .beforeValue(toJson(beforeValue))
                    .afterValue(toJson(afterValue))
                    .description(description)
                    .ipAddress(getClientIpAddress())
                    .userAgent(getUserAgent())
                    .success(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            return auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to create audit log: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Log a simple action without before/after values.
     */
    public AdminAuditLog logAction(
            String actionType,
            String targetType,
            String targetId,
            String description) {
        return logAction(actionType, targetType, targetId, null, null, null, description);
    }

    /**
     * Log a failed action.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminAuditLog logFailedAction(
            String actionType,
            String targetType,
            String targetId,
            String targetIdentifier,
            String errorMessage) {

        try {
            User admin = getCurrentAdmin();
            if (admin == null) {
                log.warn("Cannot log failed audit action - no admin user in context");
                return null;
            }

            AdminAuditLog auditLog = AdminAuditLog.builder()
                    .admin(admin)
                    .adminEmail(admin.getEmail())
                    .actionType(actionType)
                    .targetType(targetType)
                    .targetId(targetId)
                    .targetIdentifier(targetIdentifier)
                    .ipAddress(getClientIpAddress())
                    .userAgent(getUserAgent())
                    .success(false)
                    .errorMessage(errorMessage)
                    .createdAt(LocalDateTime.now())
                    .build();

            return auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to create failed audit log: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Log user-related actions (create, update, delete, etc.)
     */
    public AdminAuditLog logUserAction(
            String actionType,
            User targetUser,
            Object beforeValue,
            Object afterValue,
            String description) {
        return logAction(
                actionType,
                AdminAuditLog.TARGET_USER,
                String.valueOf(targetUser.getId()),
                targetUser.getEmail(),
                beforeValue,
                afterValue,
                description);
    }

    /**
     * Log subscription-related actions
     */
    public AdminAuditLog logSubscriptionAction(
            String actionType,
            User targetUser,
            Object beforeValue,
            Object afterValue,
            String description) {
        return logAction(
                actionType,
                AdminAuditLog.TARGET_SUBSCRIPTION,
                String.valueOf(targetUser.getId()),
                targetUser.getEmail(),
                beforeValue,
                afterValue,
                description);
    }

    /**
     * Log reseller credit actions
     */
    public AdminAuditLog logResellerCreditAction(
            String actionType,
            int resellerId,
            String resellerEmail,
            Object beforeValue,
            Object afterValue,
            String description) {
        return logAction(
                actionType,
                AdminAuditLog.TARGET_RESELLER,
                String.valueOf(resellerId),
                resellerEmail,
                beforeValue,
                afterValue,
                description);
    }

    /**
     * Get audit logs with filters
     */
    @Transactional(readOnly = true)
    public Page<AdminAuditLog> searchAuditLogs(
            Integer adminId,
            String actionType,
            String targetType,
            String targetId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int page,
            int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditLogRepository.searchAuditLogs(
                adminId, actionType, targetType, targetId, startDate, endDate, pageable);
    }

    /**
     * Get all audit logs (paginated)
     */
    @Transactional(readOnly = true)
    public Page<AdminAuditLog> getAllAuditLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditLogRepository.findAll(pageable);
    }

    /**
     * Get audit history for a specific user
     */
    @Transactional(readOnly = true)
    public Page<AdminAuditLog> getUserAuditHistory(int userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditLogRepository.findUserAuditHistory(String.valueOf(userId), pageable);
    }

    /**
     * Get recent actions by a specific admin
     */
    @Transactional(readOnly = true)
    public List<AdminAuditLog> getRecentAdminActions(String adminEmail) {
        return auditLogRepository.findTop20ByAdminEmailOrderByCreatedAtDesc(adminEmail);
    }

    /**
     * Get action statistics by type
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getActionStatsByType(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> results = auditLogRepository.countActionsByType(startDate, endDate);
        return results.stream()
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (Long) r[1]));
    }

    /**
     * Get action statistics by admin
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getActionStatsByAdmin(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> results = auditLogRepository.countActionsByAdmin(startDate, endDate);
        return results.stream()
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (Long) r[1]));
    }

    /**
     * Get failed actions
     */
    @Transactional(readOnly = true)
    public Page<AdminAuditLog> getFailedActions(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditLogRepository.findBySuccessFalseOrderByCreatedAtDesc(pageable);
    }

    /**
     * Get the current authenticated admin user
     */
    private User getCurrentAdmin() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof User) {
                return (User) authentication.getPrincipal();
            }
        } catch (Exception e) {
            log.debug("Could not get current admin: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get client IP address from the request
     */
    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                // Check for forwarded IP (proxy/load balancer)
                String forwardedFor = request.getHeader("X-Forwarded-For");
                if (forwardedFor != null && !forwardedFor.isEmpty()) {
                    return forwardedFor.split(",")[0].trim();
                }

                String realIp = request.getHeader("X-Real-IP");
                if (realIp != null && !realIp.isEmpty()) {
                    return realIp;
                }

                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Could not get client IP: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get user agent from the request
     */
    private String getUserAgent() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.debug("Could not get user agent: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Convert object to JSON string
     */
    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to convert object to JSON: {}", e.getMessage());
            return obj.toString();
        }
    }
}
