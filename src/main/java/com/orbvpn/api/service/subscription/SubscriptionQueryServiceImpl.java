package com.orbvpn.api.service.subscription;

import com.orbvpn.api.domain.entity.UserProfile;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionQueryServiceImpl implements SubscriptionQueryService {
    private final UserSubscriptionRepository userSubscriptionRepository;

    @Override
    public List<UserProfile> getUsersExpireBetween(LocalDateTime startTime, LocalDateTime endTime) {
        return userSubscriptionRepository.getUsersExpireBetween(startTime, endTime);
    }

    @Override
    public List<UserProfile> getUsersExpireAt(LocalDate localDate) {
        LocalDateTime startTime = localDate.atStartOfDay();
        LocalDateTime endTime = localDate.plusDays(1).atStartOfDay();
        return getUsersExpireBetween(startTime, endTime);
    }

    @Override
    public List<UserProfile> getUsersExpireInNextDays(Integer dayCount) {
        LocalDate localDate = LocalDate.now().plusDays(dayCount);
        return getUsersExpireAt(localDate);
    }

    @Override
    public List<UserProfile> getUsersExpireInPreviousDays(Integer dayCount) {
        LocalDate localDate = LocalDate.now().minusDays(dayCount);
        return getUsersExpireAt(localDate);
    }
}