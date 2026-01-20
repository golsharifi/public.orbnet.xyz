package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.NotificationPreferences;
import com.orbvpn.api.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferences, Long> {
    Optional<NotificationPreferences> findByUser(User user);

    Page<NotificationPreferences> findAll(Pageable pageable);

    @Modifying
    @Query("DELETE FROM NotificationPreferences np WHERE np.user = :user")
    void deleteByUser(@Param("user") User user);

}