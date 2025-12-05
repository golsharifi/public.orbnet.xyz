package com.orbvpn.api.service.referral;

import com.orbvpn.api.domain.dto.ReferralClickStatsView;
import com.orbvpn.api.domain.dto.ReferralClickStatsView.CountryStats;
import com.orbvpn.api.domain.dto.ReferralClickStatsView.DailyStats;
import com.orbvpn.api.domain.entity.ReferralConfig;
import com.orbvpn.api.domain.entity.ReferralLinkClick;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.ReferralCodeRepository;
import com.orbvpn.api.repostitory.ReferralConfigRepository;
import com.orbvpn.api.repostitory.ReferralLinkClickRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for tracking referral link clicks.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReferralClickTrackingService {

    private final ReferralLinkClickRepository clickRepository;
    private final ReferralConfigRepository configRepository;
    private final ReferralCodeRepository referralCodeRepository;

    /**
     * Track a click on a referral link.
     *
     * @param referralCode The referral code clicked
     * @param ipAddress IP address of clicker
     * @param userAgent User agent string
     * @param refererUrl Where the click came from
     * @param country Country code from geolocation
     * @return The created click record, or null if tracking disabled/rate limited
     */
    @Transactional
    public ReferralLinkClick trackClick(String referralCode, String ipAddress,
                                         String userAgent, String refererUrl, String country) {
        ReferralConfig config = configRepository.getConfig();

        if (!config.isClickTrackingEnabled()) {
            log.debug("Click tracking is disabled");
            return null;
        }

        // Find the referral code owner
        var referralCodeEntity = referralCodeRepository.findReferralCodeByCode(referralCode);
        if (referralCodeEntity == null) {
            log.warn("Click on invalid referral code: {}", referralCode);
            return null;
        }

        User referrer = referralCodeEntity.getUser();
        if (referrer == null) {
            log.warn("Referral code {} has no owner", referralCode);
            return null;
        }

        // Hash IP for privacy
        String ipHash = hashIp(ipAddress);

        // Rate limit: Check if this IP clicked recently (within 1 hour)
        long recentClicks = clickRepository.countRecentClicksByIp(ipHash, LocalDateTime.now().minusHours(1));
        if (recentClicks >= 10) {
            log.info("Rate limiting clicks from IP hash {} (10+ clicks in last hour)", ipHash);
            return null;
        }

        // Create click record
        ReferralLinkClick click = ReferralLinkClick.builder()
                .referralCode(referralCode)
                .referrer(referrer)
                .ipHash(ipHash)
                .userAgent(truncate(userAgent, 500))
                .refererUrl(truncate(refererUrl, 1000))
                .country(country)
                .converted(false)
                .build();

        click = clickRepository.save(click);
        log.debug("Tracked click {} on referral code {} from {}", click.getId(), referralCode, country);

        return click;
    }

    /**
     * Mark a click as converted (user registered).
     *
     * @param referralCode The referral code used
     * @param ipAddress IP of the registered user
     * @param newUser The newly registered user
     */
    @Transactional
    public void markConversion(String referralCode, String ipAddress, User newUser) {
        String ipHash = hashIp(ipAddress);

        // Find the most recent unconverted click from this IP with this code
        List<ReferralLinkClick> recentClicks = clickRepository
                .findByReferrerIdAndCreatedAtBetween(
                        newUser.getReferredBy() != null ? newUser.getReferredBy().getId() : 0,
                        LocalDateTime.now().minusDays(7),
                        LocalDateTime.now()
                );

        for (ReferralLinkClick click : recentClicks) {
            if (!click.isConverted() && click.getIpHash().equals(ipHash)) {
                click.markConverted(newUser);
                clickRepository.save(click);
                log.info("Marked click {} as converted for user {}", click.getId(), newUser.getId());
                return;
            }
        }

        log.debug("No matching click found for conversion (code: {}, user: {})", referralCode, newUser.getId());
    }

    /**
     * Get click statistics for a user's referral link.
     *
     * @param user The user
     * @param days Number of days to look back (0 = all time)
     * @return Click statistics
     */
    public ReferralClickStatsView getClickStats(User user, int days) {
        long totalClicks = clickRepository.countByReferrerId(user.getId());
        long conversions = clickRepository.countByReferrerIdAndConvertedTrue(user.getId());

        BigDecimal conversionRate = totalClicks > 0
                ? BigDecimal.valueOf(conversions * 100.0 / totalClicks).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Get country breakdown
        List<Object[]> countryData = clickRepository.getClickStatsByCountry(user.getId());
        List<CountryStats> countryStats = new ArrayList<>();
        for (Object[] row : countryData) {
            String countryCode = (String) row[0];
            long clicks = ((Number) row[1]).longValue();
            long convs = ((Number) row[2]).longValue();
            BigDecimal rate = clicks > 0
                    ? BigDecimal.valueOf(convs * 100.0 / clicks).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            countryStats.add(CountryStats.builder()
                    .countryCode(countryCode)
                    .countryName(countryCode) // Could lookup full name
                    .clicks(clicks)
                    .conversions(convs)
                    .conversionRate(rate)
                    .build());
        }

        // Get daily stats
        LocalDateTime startDate = days > 0
                ? LocalDateTime.now().minusDays(days)
                : LocalDateTime.now().minusDays(30);

        List<Object[]> dailyData = clickRepository.getDailyClickStats(
                user.getId(), startDate, LocalDateTime.now());
        List<DailyStats> dailyStats = new ArrayList<>();
        for (Object[] row : dailyData) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            long clicks = ((Number) row[1]).longValue();
            long convs = ((Number) row[2]).longValue();

            dailyStats.add(DailyStats.builder()
                    .date(date)
                    .clicks(clicks)
                    .conversions(convs)
                    .build());
        }

        return ReferralClickStatsView.builder()
                .totalClicks(totalClicks)
                .totalConversions(conversions)
                .conversionRate(conversionRate)
                .clicksByCountry(countryStats)
                .dailyStats(dailyStats)
                .build();
    }

    private String hashIp(String ip) {
        if (ip == null) return "unknown";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "hash-error";
        }
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}
