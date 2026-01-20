package com.orbvpn.api.service.hybrid;

import com.orbvpn.api.domain.entity.OrbMeshNode;
import com.orbvpn.api.domain.entity.OrbMeshPartner;
import com.orbvpn.api.domain.entity.OrbMeshTokenEarning;
import com.orbvpn.api.domain.enums.PartnerTier;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.OrbMeshNodeRepository;
import com.orbvpn.api.repository.OrbMeshPartnerRepository;
import com.orbvpn.api.repository.OrbMeshTokenEarningRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class OrbMeshPartnerService {

    private final OrbMeshPartnerRepository partnerRepository;
    private final OrbMeshNodeRepository nodeRepository;
    private final OrbMeshTokenEarningRepository earningRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Register a new partner
     */
    public OrbMeshPartner registerPartner(String partnerName, String contactEmail,
                                          String contactPhone, String companyName) {
        if (partnerRepository.existsByContactEmail(contactEmail)) {
            throw new IllegalArgumentException("Partner with this email already exists");
        }

        OrbMeshPartner partner = OrbMeshPartner.builder()
                .partnerUuid(UUID.randomUUID().toString())
                .partnerName(partnerName)
                .contactEmail(contactEmail)
                .contactPhone(contactPhone)
                .companyName(companyName)
                .tier(PartnerTier.BRONZE)
                .revenueSharePercent(BigDecimal.valueOf(10.00))
                .tokenBonusMultiplier(BigDecimal.ONE)
                .isVerified(false)
                .isActive(true)
                .build();

        partnerRepository.save(partner);
        log.info("Registered new partner: {} ({})", partnerName, partner.getPartnerUuid());
        return partner;
    }

    /**
     * Generate API key for partner
     */
    public String generateApiKey(Long partnerId) {
        OrbMeshPartner partner = getById(partnerId);

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        partner.setApiKeyHash(passwordEncoder.encode(apiKey));
        partner.setApiKeyLastUsedAt(LocalDateTime.now());
        partnerRepository.save(partner);

        log.info("Generated new API key for partner: {}", partner.getPartnerUuid());
        return apiKey;
    }

    /**
     * Validate API key and return partner if valid
     */
    public OrbMeshPartner validateApiKey(String apiKey) {
        List<OrbMeshPartner> activePartners = partnerRepository.findByIsActiveTrue();
        for (OrbMeshPartner partner : activePartners) {
            if (partner.getApiKeyHash() != null &&
                passwordEncoder.matches(apiKey, partner.getApiKeyHash())) {
                return partner;
            }
        }
        return null;
    }

    /**
     * Update partner information
     */
    public OrbMeshPartner updatePartner(Long partnerId, String partnerName,
                                        String contactEmail, String contactPhone,
                                        String companyName) {
        OrbMeshPartner partner = getById(partnerId);

        if (partnerName != null) partner.setPartnerName(partnerName);
        if (contactEmail != null) partner.setContactEmail(contactEmail);
        if (contactPhone != null) partner.setContactPhone(contactPhone);
        if (companyName != null) partner.setCompanyName(companyName);

        partnerRepository.save(partner);
        return partner;
    }

    /**
     * Update partner tier (admin only)
     */
    public OrbMeshPartner updateTier(Long partnerId, PartnerTier tier) {
        OrbMeshPartner partner = getById(partnerId);
        partner.setTier(tier);

        // Update revenue share based on tier
        switch (tier) {
            case BRONZE:
                partner.setRevenueSharePercent(BigDecimal.valueOf(10.00));
                partner.setTokenBonusMultiplier(BigDecimal.ONE);
                break;
            case SILVER:
                partner.setRevenueSharePercent(BigDecimal.valueOf(15.00));
                partner.setTokenBonusMultiplier(BigDecimal.valueOf(1.25));
                break;
            case GOLD:
                partner.setRevenueSharePercent(BigDecimal.valueOf(20.00));
                partner.setTokenBonusMultiplier(BigDecimal.valueOf(1.50));
                break;
            case PLATINUM:
                partner.setRevenueSharePercent(BigDecimal.valueOf(25.00));
                partner.setTokenBonusMultiplier(BigDecimal.valueOf(2.00));
                break;
        }

        partnerRepository.save(partner);
        log.info("Updated partner {} tier to {}", partner.getPartnerUuid(), tier);
        return partner;
    }

    /**
     * Verify a partner (admin only)
     */
    public OrbMeshPartner verifyPartner(Long partnerId, boolean verified, String notes) {
        OrbMeshPartner partner = getById(partnerId);
        partner.setIsVerified(verified);
        if (verified) {
            partner.setAgreementSignedAt(LocalDateTime.now());
        }
        partnerRepository.save(partner);
        log.info("Partner {} verified: {}", partner.getPartnerUuid(), verified);
        return partner;
    }

    /**
     * Deactivate a partner (admin only)
     */
    public OrbMeshPartner deactivatePartner(Long partnerId) {
        OrbMeshPartner partner = getById(partnerId);
        partner.setIsActive(false);
        partnerRepository.save(partner);
        log.info("Deactivated partner: {}", partner.getPartnerUuid());
        return partner;
    }

    /**
     * Get partner dashboard data
     */
    public PartnerDashboard getPartnerDashboard(Long partnerId) {
        OrbMeshPartner partner = getById(partnerId);
        List<OrbMeshNode> activeNodes = nodeRepository.findByPartner(partner)
                .stream()
                .filter(OrbMeshNode::getOnline)
                .toList();

        List<OrbMeshTokenEarning> recentEarnings = earningRepository
                .findByPartner(partner)
                .stream()
                .limit(10)
                .toList();

        BigDecimal pendingPayout = earningRepository.sumPendingByPartner(partner);
        if (pendingPayout == null) pendingPayout = BigDecimal.ZERO;

        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime monthEnd = LocalDateTime.now();

        BigDecimal thisMonthBandwidth = earningRepository.sumBandwidthByPartnerAndPeriod(partner, monthStart, monthEnd);
        if (thisMonthBandwidth == null) thisMonthBandwidth = BigDecimal.ZERO;

        // Calculate uptime percentage across all nodes
        BigDecimal totalUptime = BigDecimal.ZERO;
        int nodeCount = 0;
        for (OrbMeshNode node : nodeRepository.findByPartner(partner)) {
            if (node.getUptimePercentage() != null) {
                totalUptime = totalUptime.add(node.getUptimePercentage());
                nodeCount++;
            }
        }
        BigDecimal avgUptime = nodeCount > 0 ?
                totalUptime.divide(BigDecimal.valueOf(nodeCount), 2, java.math.RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        return new PartnerDashboard(
                partner,
                activeNodes,
                recentEarnings,
                pendingPayout,
                calculateThisMonthRevenue(partner),
                thisMonthBandwidth,
                avgUptime
        );
    }

    private BigDecimal calculateThisMonthRevenue(OrbMeshPartner partner) {
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        BigDecimal bandwidth = earningRepository.sumBandwidthByPartnerAndPeriod(partner, monthStart, LocalDateTime.now());
        if (bandwidth == null) return BigDecimal.ZERO;

        BigDecimal baseRevenue = bandwidth.multiply(BigDecimal.valueOf(0.01));
        return baseRevenue.multiply(partner.getRevenueSharePercent()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Update partner statistics (called by scheduled job)
     */
    public void updatePartnerStats(Long partnerId) {
        OrbMeshPartner partner = getById(partnerId);
        List<OrbMeshNode> nodes = nodeRepository.findByPartner(partner);

        int totalNodes = nodes.size();
        int staticIps = (int) nodes.stream().filter(n -> Boolean.TRUE.equals(n.getHasStaticIp())).count();

        BigDecimal totalBandwidth = nodes.stream()
                .map(OrbMeshNode::getTotalBandwidthServedGb)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTokens = earningRepository.sumPaidByPartner(partner);
        if (totalTokens == null) totalTokens = BigDecimal.ZERO;

        partner.setTotalNodes(totalNodes);
        partner.setTotalStaticIps(staticIps);
        partner.setTotalBandwidthServedGb(totalBandwidth);
        partner.setTotalTokensEarned(totalTokens);

        partnerRepository.save(partner);
    }

    // Basic CRUD operations
    public OrbMeshPartner getById(Long id) {
        return partnerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Partner not found"));
    }

    public OrbMeshPartner getByUuid(String uuid) {
        return partnerRepository.findByPartnerUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Partner not found"));
    }

    public List<OrbMeshPartner> getActivePartners() {
        return partnerRepository.findByIsVerifiedTrueAndIsActiveTrue();
    }

    public Page<OrbMeshPartner> getAllPartners(Pageable pageable) {
        return partnerRepository.findAll(pageable);
    }

    public Page<OrbMeshPartner> getPartnersByTier(PartnerTier tier, Pageable pageable) {
        return partnerRepository.findByTier(tier, pageable);
    }

    // Stats record
    public record PartnerDashboard(
            OrbMeshPartner partner,
            List<OrbMeshNode> activeNodes,
            List<OrbMeshTokenEarning> recentEarnings,
            BigDecimal pendingPayout,
            BigDecimal thisMonthRevenue,
            BigDecimal thisMonthBandwidthGb,
            BigDecimal uptimePercentage
    ) {}
}
