package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserProfileRepository extends JpaRepository<UserProfile, Integer> {

    Optional<UserProfile> findByUser(User user);

    Optional<UserProfile> findByUser_Username(String username);

    @Query("select userProfile from UserProfile userProfile where " +
            "month(userProfile.birthDate)=month(current_date()) and day(userProfile.birthDate)=day(current_date())")
    List<UserProfile> findUsersBornToday();

    // Update Telegram Chat ID by userId
    @Modifying
    @Transactional
    @Query("UPDATE UserProfile u SET u.telegramChatId = :telegramChatId WHERE u.user.id = :userId")
    void updateTelegramChatIdByUserId(int userId, String telegramChatId);

    // Update Telegram Username by userId
    @Modifying
    @Transactional
    @Query("UPDATE UserProfile u SET u.telegramUsername = :telegramUsername WHERE u.user.id = :userId")
    void updateTelegramUsernameByUserId(int userId, String telegramUsername);

    // Update Telegram Chat ID by username
    @Modifying
    @Transactional
    @Query("UPDATE UserProfile u SET u.telegramChatId = :telegramChatId WHERE u.user.username = :username")
    void updateTelegramChatIdByUsername(String username, String telegramChatId);

    // Update Telegram Username by username
    @Modifying
    @Transactional
    @Query("UPDATE UserProfile u SET u.telegramUsername = :telegramUsername WHERE u.user.username = :username")
    void updateTelegramUsernameByUsername(String username, String telegramUsername);

    @Query("SELECT u FROM UserProfile u")
    Page<UserProfile> findNextBatch(Pageable pageable);

    Optional<UserProfile> findByTelegramUsername(String telegramUsername);

    @Query("SELECT up FROM UserProfile up WHERE up.user.id = :userId")
    Optional<UserProfile> findByUser_Id(@Param("userId") int userId);
}
