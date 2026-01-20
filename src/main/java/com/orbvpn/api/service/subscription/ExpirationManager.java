package com.orbvpn.api.service.subscription;

import com.orbvpn.api.domain.entity.UserProfile;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpirationManager {
    private final UserSubscriptionRepository userSubscriptionRepository;

    public List<UserProfile> getUsersExpireBetween(LocalDateTime startTime, LocalDateTime endTime) {
        return userSubscriptionRepository.getUsersExpireBetween(startTime, endTime);
    }

    public List<UserProfile> getUsersExpireAt(LocalDate localDate) {
        LocalDateTime startTime = localDate.atStartOfDay();
        LocalDateTime endTime = localDate.plusDays(1).atStartOfDay();
        return getUsersExpireBetween(startTime, endTime);
    }

    public List<UserProfile> getUsersExpireInNextDays(Integer dayCount) {
        LocalDate localDate = LocalDate.now().plusDays(dayCount);
        return getUsersExpireAt(localDate);
    }

    public List<UserProfile> getUsersExpireInPreviousDays(Integer dayCount) {
        LocalDate localDate = LocalDate.now().minusDays(dayCount);
        return getUsersExpireAt(localDate);
    }
}
