package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.entity.AdminAuditLog;
import com.orbvpn.api.service.audit.AdminAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

/**
 * GraphQL query resolver for admin audit logs.
 * All queries require ADMIN role.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminAuditQueryResolver {

    private final AdminAuditService adminAuditService;

    /**
     * Get paginated audit logs with optional filters
     */
    @Secured(ADMIN)
    @QueryMapping
    @Transactional(readOnly = true)
    public Page<AdminAuditLog> adminAuditLogs(
            @Argument Integer adminId,
            @Argument String actionType,
            @Argument String targetType,
            @Argument String targetId,
            @Argument String startDate,
            @Argument String endDate,
            @Argument Integer page,
            @Argument Integer size) {

        LocalDateTime start = startDate != null ? LocalDateTime.parse(startDate) : null;
        LocalDateTime end = endDate != null ? LocalDateTime.parse(endDate) : null;

        return adminAuditService.searchAuditLogs(
                adminId,
                actionType,
                targetType,
                targetId,
                start,
                end,
                page != null ? page : 0,
                size != null ? size : 20);
    }

    /**
     * Get audit history for a specific user
     */
    @Secured(ADMIN)
    @QueryMapping
    @Transactional(readOnly = true)
    public Page<AdminAuditLog> userAuditHistory(
            @Argument Integer userId,
            @Argument Integer page,
            @Argument Integer size) {

        return adminAuditService.getUserAuditHistory(
                userId,
                page != null ? page : 0,
                size != null ? size : 20);
    }

    /**
     * Get recent actions by a specific admin
     */
    @Secured(ADMIN)
    @QueryMapping
    @Transactional(readOnly = true)
    public List<AdminAuditLog> recentAdminActions(@Argument String adminEmail) {
        return adminAuditService.getRecentAdminActions(adminEmail);
    }

    /**
     * Get failed actions
     */
    @Secured(ADMIN)
    @QueryMapping
    @Transactional(readOnly = true)
    public Page<AdminAuditLog> failedAdminActions(
            @Argument Integer page,
            @Argument Integer size) {

        return adminAuditService.getFailedActions(
                page != null ? page : 0,
                size != null ? size : 20);
    }

    /**
     * Get action statistics by type within a date range
     */
    @Secured(ADMIN)
    @QueryMapping
    @Transactional(readOnly = true)
    public Map<String, Long> auditStatsByAction(
            @Argument String startDate,
            @Argument String endDate) {

        LocalDateTime start = startDate != null ? LocalDateTime.parse(startDate) : LocalDateTime.now().minusDays(7);
        LocalDateTime end = endDate != null ? LocalDateTime.parse(endDate) : LocalDateTime.now();

        return adminAuditService.getActionStatsByType(start, end);
    }

    /**
     * Get action statistics by admin within a date range
     */
    @Secured(ADMIN)
    @QueryMapping
    @Transactional(readOnly = true)
    public Map<String, Long> auditStatsByAdmin(
            @Argument String startDate,
            @Argument String endDate) {

        LocalDateTime start = startDate != null ? LocalDateTime.parse(startDate) : LocalDateTime.now().minusDays(7);
        LocalDateTime end = endDate != null ? LocalDateTime.parse(endDate) : LocalDateTime.now();

        return adminAuditService.getActionStatsByAdmin(start, end);
    }
}
