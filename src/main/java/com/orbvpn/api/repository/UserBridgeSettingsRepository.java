package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.UserBridgeSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserBridgeSettingsRepository extends JpaRepository<UserBridgeSettings, Long> {

    Optional<UserBridgeSettings> findByUserId(Long userId);

    /**
     * Find settings with pessimistic write lock to prevent concurrent modifications
     * Use this for update operations to avoid "Row was updated or deleted" errors
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserBridgeSettings s WHERE s.userId = :userId")
    Optional<UserBridgeSettings> findByUserIdForUpdate(@Param("userId") Long userId);

    boolean existsByUserId(Long userId);

    /**
     * Atomic UPSERT for enabled flag - inserts if not exists, updates if exists.
     * Uses PostgreSQL ON CONFLICT to handle race conditions atomically.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO user_bridge_settings (user_id, enabled, auto_select, created_at, updated_at) " +
            "VALUES (:userId, :enabled, true, now(), now()) " +
            "ON CONFLICT (user_id) DO UPDATE SET enabled = :enabled, updated_at = now()",
            nativeQuery = true)
    void upsertEnabled(@Param("userId") Long userId, @Param("enabled") Boolean enabled);

    /**
     * Atomic UPSERT for selected bridge - inserts if not exists, updates if exists.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO user_bridge_settings (user_id, enabled, auto_select, selected_bridge_id, created_at, updated_at) " +
            "VALUES (:userId, false, :autoSelect, :bridgeId, now(), now()) " +
            "ON CONFLICT (user_id) DO UPDATE SET selected_bridge_id = :bridgeId, auto_select = :autoSelect, updated_at = now()",
            nativeQuery = true)
    void upsertSelectedBridge(@Param("userId") Long userId, @Param("bridgeId") Long bridgeId, @Param("autoSelect") Boolean autoSelect);

    /**
     * Atomic UPSERT for auto-select mode - inserts if not exists, updates if exists.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO user_bridge_settings (user_id, enabled, auto_select, created_at, updated_at) " +
            "VALUES (:userId, false, :autoSelect, now(), now()) " +
            "ON CONFLICT (user_id) DO UPDATE SET auto_select = :autoSelect, " +
            "selected_bridge_id = CASE WHEN :autoSelect = true THEN null ELSE user_bridge_settings.selected_bridge_id END, " +
            "updated_at = now()",
            nativeQuery = true)
    void upsertAutoSelect(@Param("userId") Long userId, @Param("autoSelect") Boolean autoSelect);

    /**
     * Atomic UPSERT for last used bridge - inserts if not exists, updates if exists.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO user_bridge_settings (user_id, enabled, auto_select, last_used_bridge_id, created_at, updated_at) " +
            "VALUES (:userId, false, true, :bridgeId, now(), now()) " +
            "ON CONFLICT (user_id) DO UPDATE SET last_used_bridge_id = :bridgeId, updated_at = now()",
            nativeQuery = true)
    void upsertLastUsedBridge(@Param("userId") Long userId, @Param("bridgeId") Long bridgeId);
}
