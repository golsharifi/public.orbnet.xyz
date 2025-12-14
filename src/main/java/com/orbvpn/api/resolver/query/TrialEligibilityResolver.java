package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.TrialEligibilityResponse;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.entity.TrialHistory;
import com.orbvpn.api.repository.TrialHistoryRepository;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import graphql.GraphQLException;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
@Slf4j
public class TrialEligibilityResolver {
        private final UserService userService;
        private final TrialHistoryRepository trialHistoryRepository;
        private final UserSubscriptionRepository userSubscriptionRepository;

        @QueryMapping
        @PreAuthorize("hasRole('ADMIN') or #userId == null and #username == null")
        public TrialEligibilityResponse checkTrialEligibility(
                        @Argument String deviceId,
                        @Argument Integer userId,
                        @Argument String username) {

                // Determine which user to check
                User userToCheck;
                try {
                        if (userId != null) {
                                userToCheck = userService.getUserById(userId);
                        } else if (username != null) {
                                userToCheck = userService.getUserByUsername(username);
                        } else {
                                userToCheck = userService.getUser(); // Current authenticated user
                        }
                } catch (Exception e) {
                        log.error("Error retrieving user data: {}", e.getMessage());
                        throw new GraphQLException("Error retrieving user data");
                }

                log.info("Checking trial eligibility - User: {} DeviceId: {}",
                                userToCheck.getId(), deviceId);

                try {
                        // 1. Check if user has any subscription history
                        UserSubscription previousSubscription = userSubscriptionRepository
                                        .findFirstByUserOrderByCreatedAtDesc(userToCheck);

                        if (previousSubscription != null
                                        && Boolean.TRUE.equals(previousSubscription.getIsTrialPeriod())) {
                                return TrialEligibilityResponse.builder()
                                                .eligible(false)
                                                .reason("User has already used a trial subscription")
                                                .lastTrialDate(previousSubscription.getCreatedAt())
                                                .platform(previousSubscription.getGateway().name())
                                                .build();
                        }

                        // 2. Check user's trial history
                        Optional<TrialHistory> userHistory = trialHistoryRepository
                                        .findFirstByUserIdOrderByTrialStartDateDesc(userToCheck.getId());

                        if (userHistory.isPresent()) {
                                TrialHistory history = userHistory.get();
                                return TrialEligibilityResponse.builder()
                                                .eligible(false)
                                                .reason("User has already used a trial subscription")
                                                .lastTrialDate(history.getTrialStartDate())
                                                .platform(history.getPlatform())
                                                .build();
                        }

                        // 3. Device ID is required for trial eligibility
                        // This prevents users from bypassing device-level trial tracking
                        if (deviceId == null || deviceId.trim().isEmpty()) {
                                log.warn("Trial eligibility check without device ID for user: {}",
                                                userToCheck.getId());
                                return TrialEligibilityResponse.builder()
                                                .eligible(false)
                                                .reason("Device identification required for trial eligibility")
                                                .build();
                        }

                        // 4. Check device history
                        if (trialHistoryRepository.existsByDeviceId(deviceId.trim())) {
                                return TrialEligibilityResponse.builder()
                                                .eligible(false)
                                                .reason("Device has already been used for a trial subscription")
                                                .build();
                        }

                        // 5. Check email abuse patterns
                        if (isEmailPotentiallyAbusive(userToCheck.getEmail())) {
                                return TrialEligibilityResponse.builder()
                                                .eligible(false)
                                                .reason("Email pattern not eligible for trial")
                                                .build();
                        }

                        // All checks passed
                        return TrialEligibilityResponse.builder()
                                        .eligible(true)
                                        .build();

                } catch (Exception e) {
                        log.error("Error checking trial eligibility - User: {} Device: {} Error: {}",
                                        userToCheck.getId(), deviceId, e.getMessage(), e);
                        throw new GraphQLException("Failed to check trial eligibility");
                }
        }

        private boolean isEmailPotentiallyAbusive(String email) {
                String[] suspiciousPatterns = {
                                "temp", "disposable", "minute", "hour", "throwaway",
                                "trash", "fake", "test", "tmpmail", "guerrilla",
                                "mailinator", "10minute", "yopmail"
                };

                String emailLower = email.toLowerCase();

                // Check suspicious patterns
                for (String pattern : suspiciousPatterns) {
                        if (emailLower.contains(pattern)) {
                                return true;
                        }
                }

                // Check for random-looking usernames
                if (emailLower.matches(".*\\d{4,}.*")) { // Contains 4+ consecutive digits
                        return true;
                }

                return false;
        }
}