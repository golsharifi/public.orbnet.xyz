package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.SubscriptionHistory;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.GatewayName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionHistoryRepository extends JpaRepository<SubscriptionHistory, Long> {

        long countByDeviceIdAndIsTrialTrue(String deviceId);

        long countByUserAndGatewayAndIsTrialTrue(User user, GatewayName gateway);

        List<SubscriptionHistory> findByUserIdAndIsTrialTrue(Long userId);

        List<SubscriptionHistory> findByDeviceIdAndIsTrialTrue(String deviceId);

        List<SubscriptionHistory> findByUserIdAndWasRefundedTrue(Long userId);

        List<SubscriptionHistory> findByDeviceIdAndWasRefundedTrue(String deviceId);

        List<SubscriptionHistory> findByUserEmailAndArchivedTrue(String email);

        boolean existsByUserEmailAndIsTrialTrueAndArchivedTrue(String email);

        boolean existsByDeviceIdAndIsTrialTrueAndArchivedTrue(String deviceId);

        boolean existsByDeviceIdAndGatewayAndIsTrialTrueAndStartDateAfter(
                        String deviceId, GatewayName gateway, LocalDateTime date);

        Optional<SubscriptionHistory> findByTransactionIdAndGateway(String transactionId, GatewayName gateway);

        void deleteByUserId(int userId);

        List<SubscriptionHistory> findByUserId(int userId);

        @Query("SELECT sh FROM SubscriptionHistory sh WHERE sh.subscription.id = :subscriptionId")
        List<SubscriptionHistory> findSubscriptionHistoriesBySubscriptionId(
                        @Param("subscriptionId") Integer subscriptionId);

        @Modifying
        @Query("DELETE FROM SubscriptionHistory sh WHERE sh.subscription.user.id = :userId")
        void deleteBySubscriptionUserId(Integer userId);

        @Modifying
        @Query("DELETE FROM UserSubscription us WHERE us.user.id = :userId")
        void deleteByUserId(Integer userId);

}