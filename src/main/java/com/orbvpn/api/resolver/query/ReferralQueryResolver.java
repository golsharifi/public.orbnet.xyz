package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.ReferralCodeView;
import com.orbvpn.api.domain.dto.ReferralCommissionView;
import com.orbvpn.api.domain.dto.ReferralEarningsView;
import com.orbvpn.api.domain.dto.ReferralLevelView;
import com.orbvpn.api.domain.dto.ReferralNetworkView;
import com.orbvpn.api.domain.entity.ReferralCommission;
import com.orbvpn.api.domain.entity.ReferralLevel;
import com.orbvpn.api.domain.entity.Reseller;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.repostitory.ReferralCommissionRepository;
import com.orbvpn.api.repostitory.ReferralLevelRepository;
import com.orbvpn.api.service.ReferralCodeService;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.referral.ReferralMLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.RESELLER;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

/**
 * GraphQL query resolver for referral-related queries.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ReferralQueryResolver {

    private final ReferralMLMService referralMLMService;
    private final ReferralCodeService referralCodeService;
    private final ReferralLevelRepository referralLevelRepository;
    private final ReferralCommissionRepository referralCommissionRepository;
    private final UserService userService;

    @Value("${app.referral.base-url:https://orbvpn.com/invite/}")
    private String referralBaseUrl;

    /**
     * Get the current user's referral earnings summary.
     */
    @Secured(USER)
    @QueryMapping
    public ReferralEarningsView getMyReferralEarnings() {
        User user = userService.getUser();
        log.info("Getting referral earnings for user {}", user.getId());

        ReferralMLMService.ReferralEarningsSummary summary = referralMLMService.getEarningsSummary(user);
        ReferralCodeView codeView = referralCodeService.getReferralCode();

        List<ReferralLevel> levels = referralLevelRepository.findByActiveTrueOrderByLevelAsc();

        List<ReferralEarningsView.LevelEarningsView> levelEarnings = summary.getLevelEarnings().stream()
                .map(le -> {
                    ReferralLevel levelConfig = levels.stream()
                            .filter(l -> l.getLevel() == le.getLevel())
                            .findFirst()
                            .orElse(null);

                    return ReferralEarningsView.LevelEarningsView.builder()
                            .level(le.getLevel())
                            .levelName(levelConfig != null ? levelConfig.getName() : "Level " + le.getLevel())
                            .commissionCount(le.getCommissionCount())
                            .tokensEarned(le.getTokensEarned())
                            .commissionPercent(levelConfig != null ? levelConfig.getCommissionPercent() : null)
                            .build();
                })
                .collect(Collectors.toList());

        return ReferralEarningsView.builder()
                .totalTokensEarned(summary.getTotalTokensEarned())
                .pendingTokens(summary.getPendingTokens())
                .directReferrals(summary.getDirectReferrals())
                .totalReferrals(summary.getTotalReferrals())
                .levelEarnings(levelEarnings)
                .referralCode(codeView.getCode())
                .referralLink(referralBaseUrl + codeView.getCode())
                .build();
    }

    /**
     * Get the current user's commission history.
     */
    @Secured(USER)
    @QueryMapping
    public List<ReferralCommissionView> getMyReferralCommissions(
            @Argument Integer page,
            @Argument Integer size) {

        User user = userService.getUser();
        log.info("Getting referral commissions for user {}", user.getId());

        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? Math.min(size, 100) : 20;

        Page<ReferralCommission> commissions = referralCommissionRepository
                .findByBeneficiaryOrderByCreatedAtDesc(user, PageRequest.of(pageNum, pageSize));

        return commissions.getContent().stream()
                .map(this::toCommissionView)
                .collect(Collectors.toList());
    }

    /**
     * Get all referral level configurations.
     * Available to all users so they can see potential earnings.
     */
    @Secured(USER)
    @QueryMapping
    public List<ReferralLevelView> getReferralLevels() {
        log.info("Getting referral level configurations");

        return referralLevelRepository.findByActiveTrueOrderByLevelAsc().stream()
                .map(this::toLevelView)
                .collect(Collectors.toList());
    }

    /**
     * Get all referral levels (including inactive) for admin.
     */
    @Secured(ADMIN)
    @QueryMapping
    public List<ReferralLevelView> getAllReferralLevels() {
        log.info("Admin: Getting all referral level configurations");

        return referralLevelRepository.findAll().stream()
                .map(this::toLevelView)
                .collect(Collectors.toList());
    }

    /**
     * Get referral earnings for a specific user (admin only).
     */
    @Secured(ADMIN)
    @QueryMapping
    public ReferralEarningsView getUserReferralEarnings(@Argument Integer userId) {
        User user = userService.getUserById(userId);
        log.info("Admin: Getting referral earnings for user {}", userId);

        ReferralMLMService.ReferralEarningsSummary summary = referralMLMService.getEarningsSummary(user);

        List<ReferralLevel> levels = referralLevelRepository.findByActiveTrueOrderByLevelAsc();

        List<ReferralEarningsView.LevelEarningsView> levelEarnings = summary.getLevelEarnings().stream()
                .map(le -> {
                    ReferralLevel levelConfig = levels.stream()
                            .filter(l -> l.getLevel() == le.getLevel())
                            .findFirst()
                            .orElse(null);

                    return ReferralEarningsView.LevelEarningsView.builder()
                            .level(le.getLevel())
                            .levelName(levelConfig != null ? levelConfig.getName() : "Level " + le.getLevel())
                            .commissionCount(le.getCommissionCount())
                            .tokensEarned(le.getTokensEarned())
                            .commissionPercent(levelConfig != null ? levelConfig.getCommissionPercent() : null)
                            .build();
                })
                .collect(Collectors.toList());

        // Get user's referral code if exists
        String referralCode = user.getReferralCode() != null ? user.getReferralCode().getCode() : null;

        return ReferralEarningsView.builder()
                .totalTokensEarned(summary.getTotalTokensEarned())
                .pendingTokens(summary.getPendingTokens())
                .directReferrals(summary.getDirectReferrals())
                .totalReferrals(summary.getTotalReferrals())
                .levelEarnings(levelEarnings)
                .referralCode(referralCode)
                .referralLink(referralCode != null ? referralBaseUrl + referralCode : null)
                .build();
    }

    /**
     * Get the current user's referral network (people they referred at each level).
     */
    @Secured(USER)
    @QueryMapping
    public ReferralNetworkView getMyReferralNetwork(@Argument Integer maxLevels) {
        User user = userService.getUser();
        log.info("Getting referral network for user {}", user.getId());

        int levels = maxLevels != null ? Math.min(maxLevels, 10) : 3;
        return buildNetworkView(user, levels, false);
    }

    /**
     * Get a specific user's referral network (admin/reseller only).
     */
    @Secured({ADMIN, RESELLER})
    @QueryMapping
    public ReferralNetworkView getUserReferralNetwork(
            @Argument Integer userId,
            @Argument Integer maxLevels) {

        User accessor = userService.getUser();
        User targetUser = userService.getUserById(userId);

        // Resellers can only view their own users' networks
        if (!userService.isAdmin()) {
            Reseller reseller = accessor.getReseller();
            if (reseller == null || targetUser.getReseller() == null ||
                    reseller.getId() != targetUser.getReseller().getId()) {
                throw new IllegalStateException("You can only view referral networks for your own users");
            }
        }

        log.info("Getting referral network for user {} by {}", userId, accessor.getId());

        int levels = maxLevels != null ? Math.min(maxLevels, 10) : 3;
        return buildNetworkView(targetUser, levels, userService.isAdmin());
    }

    /**
     * Get referral commission history for a specific user (admin/reseller only).
     */
    @Secured({ADMIN, RESELLER})
    @QueryMapping
    public List<ReferralCommissionView> getUserReferralCommissions(
            @Argument Integer userId,
            @Argument Integer page,
            @Argument Integer size) {

        User accessor = userService.getUser();
        User targetUser = userService.getUserById(userId);

        // Resellers can only view their own users' commissions
        if (!userService.isAdmin()) {
            Reseller reseller = accessor.getReseller();
            if (reseller == null || targetUser.getReseller() == null ||
                    reseller.getId() != targetUser.getReseller().getId()) {
                throw new IllegalStateException("You can only view commissions for your own users");
            }
        }

        log.info("Getting referral commissions for user {} by {}", userId, accessor.getId());

        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? Math.min(size, 100) : 20;

        Page<ReferralCommission> commissions = referralCommissionRepository
                .findByBeneficiaryOrderByCreatedAtDesc(targetUser, PageRequest.of(pageNum, pageSize));

        // Admin sees full emails, resellers see masked
        boolean showFullEmail = userService.isAdmin();

        return commissions.getContent().stream()
                .map(c -> toCommissionView(c, showFullEmail))
                .collect(Collectors.toList());
    }

    private ReferralNetworkView buildNetworkView(User user, int maxLevels, boolean showFullEmail) {
        Map<Integer, List<User>> network = referralMLMService.getDownlineNetwork(user, maxLevels);
        List<ReferralLevel> levelConfigs = referralLevelRepository.findByActiveTrueOrderByLevelAsc();

        List<ReferralNetworkView.ReferralLevelNetwork> levelNetworks = new ArrayList<>();
        long totalNetworkSize = 0;

        for (Map.Entry<Integer, List<User>> entry : network.entrySet()) {
            int level = entry.getKey();
            List<User> usersAtLevel = entry.getValue();
            totalNetworkSize += usersAtLevel.size();

            ReferralLevel levelConfig = levelConfigs.stream()
                    .filter(l -> l.getLevel() == level)
                    .findFirst()
                    .orElse(null);

            BigDecimal totalEarningsFromLevel = referralCommissionRepository
                    .getTotalTokensEarnedByUserAtLevel(user, level);

            List<ReferralNetworkView.ReferredUserView> userViews = usersAtLevel.stream()
                    .map(u -> {
                        BigDecimal commission = referralMLMService.getTotalCommissionFromUser(user, u);
                        UserSubscription sub = u.getCurrentSubscription();
                        boolean hasActiveSub = sub != null && sub.getExpiresAt() != null &&
                                sub.getExpiresAt().isAfter(LocalDateTime.now());

                        return ReferralNetworkView.ReferredUserView.builder()
                                .userId(u.getId())
                                .email(showFullEmail ? u.getEmail() : maskEmail(u.getEmail()))
                                .username(u.getUsername())
                                .joinedAt(u.getCreatedAt())
                                .totalCommissionFromUser(commission)
                                .hasActiveSubscription(hasActiveSub)
                                .build();
                    })
                    .collect(Collectors.toList());

            levelNetworks.add(ReferralNetworkView.ReferralLevelNetwork.builder()
                    .level(level)
                    .levelName(levelConfig != null ? levelConfig.getName() : "Level " + level)
                    .userCount(usersAtLevel.size())
                    .totalEarningsFromLevel(totalEarningsFromLevel)
                    .users(userViews)
                    .build());
        }

        return ReferralNetworkView.builder()
                .totalNetworkSize(totalNetworkSize)
                .levels(levelNetworks)
                .build();
    }

    private ReferralCommissionView toCommissionView(ReferralCommission commission) {
        return toCommissionView(commission, false);
    }

    private ReferralCommissionView toCommissionView(ReferralCommission commission, boolean showFullEmail) {
        String sourceEmail = commission.getSourceUser() != null
                ? (showFullEmail ? commission.getSourceUser().getEmail() : maskEmail(commission.getSourceUser().getEmail()))
                : "Unknown";

        return ReferralCommissionView.builder()
                .id(commission.getId())
                .sourceUserEmail(sourceEmail)
                .level(commission.getLevel())
                .paymentAmount(commission.getPaymentAmount())
                .commissionPercent(commission.getCommissionPercent())
                .tokenAmount(commission.getTokenAmount())
                .status(commission.getStatus())
                .createdAt(commission.getCreatedAt())
                .creditedAt(commission.getCreditedAt())
                .build();
    }

    private ReferralLevelView toLevelView(ReferralLevel level) {
        return ReferralLevelView.builder()
                .id(level.getId())
                .level(level.getLevel())
                .name(level.getName())
                .description(level.getDescription())
                .commissionPercent(level.getCommissionPercent())
                .minimumTokens(level.getMinimumTokens())
                .maximumTokens(level.getMaximumTokens())
                .active(level.isActive())
                .build();
    }

    /**
     * Mask email for privacy (show first 2 chars and domain).
     */
    private String maskEmail(String email) {
        if (email == null || email.length() < 5) {
            return "***@***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***@***";
        }
        String prefix = email.substring(0, Math.min(2, atIndex));
        String domain = email.substring(atIndex);
        return prefix + "***" + domain;
    }
}
