package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for AdminAuditLog entity.
 * Provides methods to query audit logs with various filters.
 */
@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    /**
     * Find all audit logs for a specific admin
     */
    Page<AdminAuditLog> findByAdminIdOrderByCreatedAtDesc(Integer adminId, Pageable pageable);

    /**
     * Find all audit logs by action type
     */
    Page<AdminAuditLog> findByActionTypeOrderByCreatedAtDesc(String actionType, Pageable pageable);

    /**
     * Find all audit logs for a specific target
     */
    Page<AdminAuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            String targetType, String targetId, Pageable pageable);

    /**
     * Find all audit logs within a date range
     */
    Page<AdminAuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Search audit logs with multiple filters
     */
    @Query("SELECT a FROM AdminAuditLog a WHERE " +
           "(:adminId IS NULL OR a.admin.id = :adminId) AND " +
           "(:actionType IS NULL OR a.actionType = :actionType) AND " +
           "(:targetType IS NULL OR a.targetType = :targetType) AND " +
           "(:targetId IS NULL OR a.targetId = :targetId) AND " +
           "(:startDate IS NULL OR a.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR a.createdAt <= :endDate) " +
           "ORDER BY a.createdAt DESC")
    Page<AdminAuditLog> searchAuditLogs(
            @Param("adminId") Integer adminId,
            @Param("actionType") String actionType,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Get recent actions by admin email (for quick lookup)
     */
    List<AdminAuditLog> findTop20ByAdminEmailOrderByCreatedAtDesc(String adminEmail);

    /**
     * Get all actions on a specific user
     */
    @Query("SELECT a FROM AdminAuditLog a WHERE " +
           "a.targetType = 'USER' AND a.targetId = :userId " +
           "ORDER BY a.createdAt DESC")
    Page<AdminAuditLog> findUserAuditHistory(@Param("userId") String userId, Pageable pageable);

    /**
     * Count actions by type within a date range
     */
    @Query("SELECT a.actionType, COUNT(a) FROM AdminAuditLog a " +
           "WHERE a.createdAt >= :startDate AND a.createdAt <= :endDate " +
           "GROUP BY a.actionType")
    List<Object[]> countActionsByType(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count actions by admin within a date range
     */
    @Query("SELECT a.adminEmail, COUNT(a) FROM AdminAuditLog a " +
           "WHERE a.createdAt >= :startDate AND a.createdAt <= :endDate " +
           "GROUP BY a.adminEmail ORDER BY COUNT(a) DESC")
    List<Object[]> countActionsByAdmin(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find failed actions
     */
    Page<AdminAuditLog> findBySuccessFalseOrderByCreatedAtDesc(Pageable pageable);
}
