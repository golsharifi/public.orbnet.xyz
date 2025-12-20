package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.Reseller;
import com.orbvpn.api.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

        Optional<User> findByOauthId(String oauthId);

        Optional<User> findByUsername(String username);

        Optional<User> findByUsernameAndOauthId(String username, String oauthId);

        @Query("SELECT u FROM User u WHERE u.email = :email")
        Optional<User> findByEmail(@Param("email") String email);

        Optional<User> findByEmailAndActiveTrue(String email);

        Optional<User> findByUsernameAndActiveTrue(String username);

        long countByCreatedAtAfter(LocalDateTime createdAt);

        // New query to fetch users in a specific ID range
        List<User> findByIdBetween(int fromUserId, int toUserId);

        List<User> findAllByActiveTrue();

        Page<User> findAllByReseller(Reseller reseller, Pageable pageable);

        @Query("SELECT user from User user where user.id not in (select sub.user.id from UserSubscription sub where sub.expiresAt > :dateTime)")
        Page<User> findAllExpiredUsers(LocalDateTime dateTime, Pageable pageable);

        @Query("SELECT user from User user where user.reseller = :reseller and user.id not in (select sub.user.id from UserSubscription sub where sub.expiresAt > :dateTime)")
        Page<User> findAllResellerExpiredUsers(Reseller reseller, LocalDateTime dateTime, Pageable pageable);

        @Query(value = "SELECT count(*) FROM \"user\" where role_id=3 and email in (select username from radacct where acctstoptime IS NULL)", nativeQuery = true)
        int getTotalActiveUsers();

        @Query(value = "SELECT count(*) FROM \"user\" where role_id=3 and reseller_id=:resellerId and email in (select username from radacct where acctstoptime IS NULL)", nativeQuery = true)
        int getActiveUsersOfReseller(int resellerId);

        @Query(value = "SELECT * FROM user_profile user_profile join \"user\" on \"user\".id=user_profile.user_id where \"user\".role_id=3 and \"user\".email in (select username from radacct where acctstoptime IS NULL)", countQuery = "SELECT count(*) FROM \"user\" join user_profile on \"user\".id=user_profile.user_id where role_id=3 and email in (select username from radacct where acctstoptime IS NULL)", nativeQuery = true)
        Page<User> findAllActiveUsers(Pageable pageable);

        @Query(value = "SELECT * FROM user_profile user_profile join \"user\" on \"user\".id=user_profile.user_id where \"user\".role_id=3 and \"user\".email not in (select username from radacct where acctstoptime IS NULL)", countQuery = "SELECT count(*) FROM \"user\" join user_profile on \"user\".id=user_profile.user_id where role_id=3 and email not in (select username from radacct where acctstoptime IS NULL)", nativeQuery = true)
        Page<User> findAllNotActiveUsers(Pageable pageable);

        // @Query(
        // value = "select u from User u where u.role.id = 3 and ",
        // countQuery = "SELECT count(*) FROM User where role_id=3"
        // )
        Page<User> findByRoleIdAndEmailContaining(int roleId, String email, Pageable pageable);

        Page<User> findByRoleIdAndUsernameContaining(int roleId, String username, Pageable pageable);

        @Query("select u from User u where u.role.id = 3 and :param like '%:query%'")
        Page<User> findByParam(@Param("param") String param, @Param("query") String query, Pageable pageable);

        @Query(value = "SELECT * FROM \"user\" WHERE role_id=3 AND email LIKE CONCAT('%', ?1, '%')", countQuery = "SELECT COUNT(*) FROM \"user\" WHERE role_id=3 AND email LIKE CONCAT('%', ?1, '%')", nativeQuery = true)
        Page<User> findAllUsers(String query, Pageable pageable);

        @Query(value = "select distinct(connectinfo_start) from radacct where username = :username", nativeQuery = true)
        List<String> findAllUserDevices(String username);

        @Query(value = "select distinct(connectinfo_start) from radacct where username = :username and acctstoptime is null", nativeQuery = true)
        List<String> findAllActiveUserDevices(String username);

        @Query(value = "select \"user\".* " +
                        "FROM \"user\" " +
                        "         LEFT JOIN radacct radAcct ON radAcct.username = \"user\".email " +
                        "         LEFT JOIN user_subscription subs ON \"user\".id = subs.user_id " +
                        "         LEFT JOIN group_app g ON subs.group_id = g.id " +
                        "         LEFT JOIN service_group sg ON g.service_group_id = sg.id " +
                        "         LEFT JOIN role r ON \"user\".role_id = r.id " +
                        "         LEFT JOIN server ON radAcct.nasipaddress = server.private_ip " +
                        "WHERE radAcct.acctstoptime IS NULL " +
                        "  AND (:roleId IS NULL OR r.id = :roleId) " +
                        "  AND (:groupId IS NULL OR g.id = :groupId) " +
                        "  AND (:serverId IS NULL OR server.id = :serverId) " +
                        "  AND (:serviceGroupId IS NULL OR :serviceGroupId = sg.id)", countQuery = "SELECT count(\"user\".email) "
                                        +
                                        "FROM \"user\" " +
                                        "         LEFT JOIN radacct radAcct ON radAcct.username = \"user\".email " +
                                        "         LEFT JOIN user_subscription subs ON \"user\".id = subs.user_id " +
                                        "         LEFT JOIN group_app g ON subs.group_id = g.id " +
                                        "         LEFT JOIN service_group sg ON g.service_group_id = sg.id " +
                                        "         LEFT JOIN role r ON \"user\".role_id = r.id " +
                                        "         LEFT JOIN server ON radAcct.nasipaddress = server.private_ip " +
                                        "WHERE radAcct.acctstoptime IS NULL " +
                                        "  AND (:roleId IS NULL OR r.id = :roleId) " +
                                        "  AND (:groupId IS NULL OR g.id = :groupId) " +
                                        "  AND (:serverId IS NULL OR server.id = :serverId) " +
                                        "  AND (:serviceGroupId IS NULL OR :serviceGroupId = sg.id)", nativeQuery = true)
        Page<User> findOnlineUsers(Pageable pageable, Integer serverId, Integer groupId, Integer roleId,
                        Integer serviceGroupId);

        @Modifying
        @Query(value = "update \"user\" set reseller_id=:new_id where reseller_id=:old_id", nativeQuery = true)
        int updateResellerId(@Param("old_id") int oldId, @Param("new_id") int newId);

        @Query("SELECT u FROM User u WHERE " +
                        "LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                        "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
        Page<User> searchUsers(@Param("searchTerm") String searchTerm, Pageable pageable);

        @Query("SELECT DISTINCT u FROM User u " +
                        "LEFT JOIN FETCH u.userSubscriptionList s " + // Changed from subscription to
                                                                      // userSubscriptionList
                        "LEFT JOIN FETCH s.group g " +
                        "LEFT JOIN FETCH g.serviceGroup sg " +
                        "WHERE u.id = :id")
        Optional<User> findByIdWithSubscriptionChain(@Param("id") Integer id);

        @Query("SELECT DISTINCT u FROM User u " +
                        "LEFT JOIN FETCH u.userSubscriptionList s " + // Changed from subscription to
                                                                      // userSubscriptionList
                        "LEFT JOIN FETCH s.group g " +
                        "LEFT JOIN FETCH g.serviceGroup sg " +
                        "WHERE u.email = :email")
        Optional<User> findByEmailWithSubscriptionChain(@Param("email") String email);

        @Query("SELECT u FROM User u " +
                        "LEFT JOIN FETCH u.userSubscriptionList s " +
                        "WHERE u.email = :email " +
                        "AND s.expiresAt > CURRENT_TIMESTAMP")
        Optional<User> findByEmailWithActiveSubscription(@Param("email") String email);

        @Query("SELECT u FROM User u LEFT JOIN FETCH u.userSubscriptionList s WHERE u.email = :email")
        Optional<User> findByEmailWithSubscription(@Param("email") String email);

        @Query("SELECT DISTINCT u FROM User u " +
                        "LEFT JOIN FETCH u.profile p " +
                        "LEFT JOIN FETCH u.userSubscriptionList s " +
                        "WHERE u.id = :id")
        Optional<User> findByIdWithDetails(@Param("id") int id);

        @Query("SELECT u FROM User u WHERE u.uuid IS NULL")
        List<User> findByUuidIsNull();

        @Query(value = "SELECT COUNT(*) FROM \"user\" WHERE uuid IS NULL", nativeQuery = true)
        long countByUuidIsNull();

        @Query(value = "SELECT * FROM \"user\" WHERE uuid IS NULL ORDER BY id LIMIT 1", nativeQuery = true)
        Optional<User> findFirstByUuidIsNull();

        @Query("SELECT u FROM User u WHERE u.role.id = :roleId")
        List<User> findByRoleId(@Param("roleId") Integer roleId);

        // Or if you have a specific admin role ID (e.g., role_id = 1 for admins)
        @Query("SELECT u FROM User u WHERE u.role.id = 1")
        List<User> findAdminUsers();

        @Modifying
        @Query(value = "DELETE FROM \"user\" WHERE id = :userId", nativeQuery = true)
        void forceDelete(@Param("userId") int userId);

        @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.id = :userId")
        boolean existsById(@Param("userId") int userId);

        // Native queries for UUID migration (avoiding entity mapping issues)
        @Query(value = "SELECT * FROM \"user\" WHERE uuid IS NULL ORDER BY id LIMIT 1", nativeQuery = true)
        Optional<User> findFirstByUuidIsNullNative();

        @Query(value = "SELECT * FROM \"user\" WHERE id = :userId", nativeQuery = true)
        Optional<User> findByIdNative(@Param("userId") int userId);

        @Query(value = "SELECT * FROM \"user\"", countQuery = "SELECT COUNT(*) FROM \"user\"", nativeQuery = true)
        Page<User> findAllNative(Pageable pageable);

        // Simple native queries for UUID migration - these return primitive types to
        // avoid casting issues
        @Query(value = "SELECT id FROM \"user\" WHERE uuid IS NULL ORDER BY id LIMIT 1", nativeQuery = true)
        List<Integer> findFirstUserIdWithoutUuid();

        @Query(value = "SELECT email FROM \"user\" WHERE id = ?1", nativeQuery = true)
        String findEmailById(int userId);

        @Query(value = "SELECT uuid FROM \"user\" WHERE id = ?1", nativeQuery = true)
        String findUuidById(int userId);

        @Query(value = "UPDATE \"user\" SET uuid = ?2 WHERE id = ?1", nativeQuery = true)
        @Modifying
        int updateUuidById(int userId, String uuid);

        // Legacy raw query methods (for Object[] returns - not recommended due to
        // casting issues)
        @Query(value = "SELECT id, email, uuid FROM \"user\" WHERE uuid IS NULL ORDER BY id LIMIT 1", nativeQuery = true)
        Optional<Object[]> findFirstByUuidIsNullRaw();

        @Query(value = "SELECT id, email, uuid FROM \"user\" WHERE id = :userId", nativeQuery = true)
        Optional<Object[]> findByIdRaw(@Param("userId") int userId);

        @Query(value = "SELECT country, COUNT(*) as count FROM \"user\" WHERE country IS NOT NULL AND country != '' GROUP BY country ORDER BY count DESC", nativeQuery = true)
        List<Object[]> countUsersByCountry();

        // MLM Referral queries
        long countByReferredByIsNotNull();

        long countByReferredById(int referredById);

        List<User> findByReferredById(int referredById);

}