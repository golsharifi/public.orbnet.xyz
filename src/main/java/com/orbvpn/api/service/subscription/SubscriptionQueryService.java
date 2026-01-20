package com.orbvpn.api.service.subscription;

import com.orbvpn.api.domain.entity.UserProfile;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionQueryService {
    List<UserProfile> getUsersExpireBetween(LocalDateTime startTime, LocalDateTime endTime);

    List<UserProfile> getUsersExpireAt(LocalDate localDate);

    List<UserProfile> getUsersExpireInNextDays(Integer dayCount);

    List<UserProfile> getUsersExpireInPreviousDays(Integer dayCount);
}