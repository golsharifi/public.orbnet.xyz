package com.orbvpn.api.service.partner;

import com.orbvpn.api.domain.dto.partner.*;
import com.orbvpn.api.domain.entity.OrbMeshNode;
import com.orbvpn.api.domain.entity.OrbMeshPartner;
import com.orbvpn.api.domain.enums.DeploymentType;
import com.orbvpn.api.domain.enums.PartnerTier;
import com.orbvpn.api.repository.OrbMeshNodeRepository;
import com.orbvpn.api.repository.OrbMeshPartnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerService {

    private final OrbMeshPartnerRepository partnerRepository;
    private final OrbMeshNodeRepository nodeRepository;
    private final PasswordEncoder passwordEncoder;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ========================== PARTNER CRUD ==========================

    @Transactional
    public PartnerDTO createPartner(CreatePartnerInput input) {
        log.info("Creating new partner: {}", input.getPartnerName());

        OrbMeshPartner partner = OrbMeshPartner.builder()
                .partnerUuid(UUID.randomUUID().toString())
                .partnerName(input.getPartnerName())
                .contactEmail(input.getContactEmail())
                .contactPhone(input.getContactPhone())
                .companyName(input.getCompanyName())
                .countryCode(input.getCountryCode())
                .tier(PartnerTier.BRONZE)
                .revenueSharePercent(PartnerTier.BRONZE.getRevenueSharePercent())
                .tokenBonusMultiplier(PartnerTier.BRONZE.getTokenBonusMultiplier())
                .isVerified(false)
                .isActive(true)
                .build();

        partner = partnerRepository.save(partner);
        log.info("Created partner with ID: {}", partner.getId());

        return toDTO(partner);
    }

    @Transactional
    public PartnerDTO updatePartner(UpdatePartnerInput input) {
        OrbMeshPartner partner = partnerRepository.findById(input.getPartnerId())
                .orElseThrow(() -> new RuntimeException("Partner not found: " + input.getPartnerId()));

        if (input.getPartnerName() != null) partner.setPartnerName(input.getPartnerName());
        if (input.getContactEmail() != null) partner.setContactEmail(input.getContactEmail());
        if (input.getContactPhone() != null) partner.setContactPhone(input.getContactPhone());
        if (input.getCompanyName() != null) partner.setCompanyName(input.getCompanyName());
        if (input.getCountryCode() != null) partner.setCountryCode(input.getCountryCode());
        if (input.getTier() != null) {
            partner.setTier(input.getTier());
            partner.setRevenueSharePercent(input.getTier().getRevenueSharePercent());
            partner.setTokenBonusMultiplier(input.getTier().getTokenBonusMultiplier());
        }
        if (input.getRevenueSharePercent() != null) partner.setRevenueSharePercent(input.getRevenueSharePercent());
        if (input.getTokenBonusMultiplier() != null) partner.setTokenBonusMultiplier(input.getTokenBonusMultiplier());
        if (input.getIsActive() != null) partner.setIsActive(input.getIsActive());
        if (input.getIsVerified() != null) {
            partner.setIsVerified(input.getIsVerified());
            if (input.getIsVerified() && partner.getAgreementSignedAt() == null) {
                partner.setAgreementSignedAt(LocalDateTime.now());
                partner.setAgreementExpiresAt(LocalDateTime.now().plusYears(1));
            }
        }

        partner = partnerRepository.save(partner);
        log.info("Updated partner: {}", partner.getId());

        return toDTO(partner);
    }

    public PartnerDTO getPartner(Long partnerId) {
        return partnerRepository.findById(partnerId)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Partner not found: " + partnerId));
    }

    public PartnerDTO getPartnerByUuid(String partnerUuid) {
        return partnerRepository.findByPartnerUuid(partnerUuid)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Partner not found: " + partnerUuid));
    }

    public Page<PartnerDTO> getAllPartners(int page, int size, Boolean isActive) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrbMeshPartner> partners;

        if (isActive != null) {
            partners = partnerRepository.findByIsActive(isActive, pageable);
        } else {
            partners = partnerRepository.findAll(pageable);
        }

        return partners.map(this::toDTO);
    }

    // ========================== API KEY MANAGEMENT ==========================

    @Transactional
    public String generateApiKey(Long partnerId) {
        OrbMeshPartner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new RuntimeException("Partner not found: " + partnerId));

        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String fullApiKey = "orb_" + partner.getPartnerUuid().substring(0, 8) + "_" + apiKey;

        partner.setApiKeyHash(passwordEncoder.encode(fullApiKey));
        partnerRepository.save(partner);

        log.info("Generated new API key for partner: {}", partnerId);
        return fullApiKey;
    }

    public OrbMeshPartner validateApiKey(String apiKey) {
        if (apiKey == null || !apiKey.startsWith("orb_")) {
            return null;
        }

        String partnerUuidPrefix = apiKey.substring(4, 12);
        List<OrbMeshPartner> potentialPartners = partnerRepository.findByPartnerUuidStartingWith(partnerUuidPrefix);

        for (OrbMeshPartner partner : potentialPartners) {
            if (partner.getApiKeyHash() != null && passwordEncoder.matches(apiKey, partner.getApiKeyHash())) {
                partner.setApiKeyLastUsedAt(LocalDateTime.now());
                partnerRepository.save(partner);
                return partner;
            }
        }

        return null;
    }

    // ========================== NODE MANAGEMENT ==========================

    @Transactional
    public PartnerNodeDTO registerNode(Long partnerId, RegisterNodeInput input) {
        OrbMeshPartner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new RuntimeException("Partner not found: " + partnerId));

        if (!partner.getIsActive() || !partner.getIsVerified()) {
            throw new RuntimeException("Partner is not active or not verified");
        }

        OrbMeshNode node = OrbMeshNode.builder()
                .nodeUuid(UUID.randomUUID().toString())
                .partner(partner)
                .deploymentType(DeploymentType.PARTNER_DC)
                .publicIp(input.getPublicIp())
                .ddnsHostname(input.getDdnsHostname())
                .region(input.getRegion())
                .regionDisplayName(input.getRegionDisplayName())
                .countryCode(input.getCountryCode())
                .hasStaticIp(input.getHasStaticIp() != null ? input.getHasStaticIp() : false)
                .supportsPortForward(input.getSupportsPortForward() != null ? input.getSupportsPortForward() : false)
                .supportsBridgeNode(input.getSupportsBridgeNode() != null ? input.getSupportsBridgeNode() : false)
                .supportsAi(input.getSupportsAi() != null ? input.getSupportsAi() : false)
                .isBehindCgnat(input.getIsBehindCgnat() != null ? input.getIsBehindCgnat() : false)
                .canEarnTokens(input.getCanEarnTokens() != null ? input.getCanEarnTokens() : true)
                .uploadMbps(input.getUploadMbps())
                .downloadMbps(input.getDownloadMbps())
                .maxConnections(input.getMaxConnections() != null ? input.getMaxConnections() : 100)
                .cpuCores(input.getCpuCores())
                .ramMb(input.getRamMb())
                .deviceType(input.getDeviceType())
                .softwareVersion(input.getSoftwareVersion())
                .online(false)
                .isMiningEnabled(true)
                .build();

        node = nodeRepository.save(node);

        // Update partner stats
        partner.setTotalNodes(partner.getTotalNodes() + 1);
        if (node.getHasStaticIp()) {
            partner.setTotalStaticIps(partner.getTotalStaticIps() + 1);
        }
        partnerRepository.save(partner);

        log.info("Registered new node {} for partner {}", node.getNodeUuid(), partnerId);

        return toNodeDTO(node);
    }

    @Transactional
    public void removeNode(Long partnerId, String nodeUuid) {
        OrbMeshNode node = nodeRepository.findByNodeUuid(nodeUuid)
                .orElseThrow(() -> new RuntimeException("Node not found: " + nodeUuid));

        if (node.getPartner() == null || !node.getPartner().getId().equals(partnerId)) {
            throw new RuntimeException("Node does not belong to this partner");
        }

        OrbMeshPartner partner = node.getPartner();
        partner.setTotalNodes(Math.max(0, partner.getTotalNodes() - 1));
        if (node.getHasStaticIp()) {
            partner.setTotalStaticIps(Math.max(0, partner.getTotalStaticIps() - 1));
        }
        partnerRepository.save(partner);

        nodeRepository.delete(node);
        log.info("Removed node {} from partner {}", nodeUuid, partnerId);
    }

    public Page<PartnerNodeDTO> getPartnerNodes(Long partnerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrbMeshNode> nodes = nodeRepository.findByPartnerId(partnerId, pageable);
        return nodes.map(this::toNodeDTO);
    }

    public List<PartnerNodeDTO> getOnlineNodes(Long partnerId) {
        List<OrbMeshNode> nodes = nodeRepository.findByPartnerIdAndOnline(partnerId, true);
        return nodes.stream().map(this::toNodeDTO).collect(Collectors.toList());
    }

    // ========================== STATISTICS ==========================

    public PartnerAdminStatsDTO getAdminStats() {
        List<OrbMeshPartner> allPartners = partnerRepository.findAll();

        int totalPartners = allPartners.size();
        int activePartners = (int) allPartners.stream().filter(OrbMeshPartner::getIsActive).count();
        int verifiedPartners = (int) allPartners.stream().filter(OrbMeshPartner::getIsVerified).count();

        List<OrbMeshNode> partnerNodes = nodeRepository.findByDeploymentType(DeploymentType.PARTNER_DC);
        int totalNodes = partnerNodes.size();
        int onlineNodes = (int) partnerNodes.stream().filter(OrbMeshNode::getOnline).count();

        BigDecimal totalBandwidth = allPartners.stream()
                .map(OrbMeshPartner::getTotalBandwidthServedGb)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRevenueShared = allPartners.stream()
                .map(OrbMeshPartner::getTotalRevenueEarned)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTokensEarned = allPartners.stream()
                .map(OrbMeshPartner::getTotalTokensEarned)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PartnerAdminStatsDTO.TierDistribution> tierDistribution = List.of(
                buildTierDistribution(allPartners, partnerNodes, PartnerTier.BRONZE),
                buildTierDistribution(allPartners, partnerNodes, PartnerTier.SILVER),
                buildTierDistribution(allPartners, partnerNodes, PartnerTier.GOLD),
                buildTierDistribution(allPartners, partnerNodes, PartnerTier.PLATINUM)
        );

        return PartnerAdminStatsDTO.builder()
                .totalPartners(totalPartners)
                .activePartners(activePartners)
                .verifiedPartners(verifiedPartners)
                .totalNodes(totalNodes)
                .onlineNodes(onlineNodes)
                .totalBandwidthServedGb(totalBandwidth)
                .totalRevenueShared(totalRevenueShared)
                .totalTokensEarned(totalTokensEarned)
                .tierDistribution(tierDistribution)
                .build();
    }

    private PartnerAdminStatsDTO.TierDistribution buildTierDistribution(
            List<OrbMeshPartner> partners, List<OrbMeshNode> nodes, PartnerTier tier) {
        List<OrbMeshPartner> tierPartners = partners.stream()
                .filter(p -> p.getTier() == tier)
                .collect(Collectors.toList());

        int partnerCount = tierPartners.size();
        int nodeCount = (int) nodes.stream()
                .filter(n -> n.getPartner() != null && n.getPartner().getTier() == tier)
                .count();
        BigDecimal bandwidth = tierPartners.stream()
                .map(OrbMeshPartner::getTotalBandwidthServedGb)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PartnerAdminStatsDTO.TierDistribution.builder()
                .tier(tier.name())
                .count(partnerCount)
                .totalNodes(nodeCount)
                .totalBandwidth(bandwidth)
                .build();
    }

    // ========================== HELPERS ==========================

    private PartnerDTO toDTO(OrbMeshPartner partner) {
        return PartnerDTO.builder()
                .id(partner.getId())
                .partnerUuid(partner.getPartnerUuid())
                .partnerName(partner.getPartnerName())
                .contactEmail(partner.getContactEmail())
                .contactPhone(partner.getContactPhone())
                .companyName(partner.getCompanyName())
                .countryCode(partner.getCountryCode())
                .tier(partner.getTier())
                .revenueSharePercent(partner.getRevenueSharePercent())
                .tokenBonusMultiplier(partner.getTokenBonusMultiplier())
                .agreementSignedAt(partner.getAgreementSignedAt())
                .agreementExpiresAt(partner.getAgreementExpiresAt())
                .isVerified(partner.getIsVerified())
                .isActive(partner.getIsActive())
                .totalNodes(partner.getTotalNodes())
                .totalStaticIps(partner.getTotalStaticIps())
                .totalBandwidthServedGb(partner.getTotalBandwidthServedGb())
                .totalRevenueEarned(partner.getTotalRevenueEarned())
                .totalTokensEarned(partner.getTotalTokensEarned())
                .createdAt(partner.getCreatedAt())
                .updatedAt(partner.getUpdatedAt())
                .build();
    }

    private PartnerNodeDTO toNodeDTO(OrbMeshNode node) {
        return PartnerNodeDTO.builder()
                .id(node.getId())
                .nodeUuid(node.getNodeUuid())
                .partnerId(node.getPartner() != null ? node.getPartner().getId() : null)
                .partnerName(node.getPartner() != null ? node.getPartner().getPartnerName() : null)
                .deploymentType(node.getDeploymentType())
                .publicIp(node.getPublicIp())
                .ddnsHostname(node.getDdnsHostname())
                .region(node.getRegion())
                .regionDisplayName(node.getRegionDisplayName())
                .countryCode(node.getCountryCode())
                .hasStaticIp(node.getHasStaticIp())
                .supportsPortForward(node.getSupportsPortForward())
                .supportsBridgeNode(node.getSupportsBridgeNode())
                .supportsAi(node.getSupportsAi())
                .isBehindCgnat(node.getIsBehindCgnat())
                .canEarnTokens(node.getCanEarnTokens())
                .uploadMbps(node.getUploadMbps())
                .downloadMbps(node.getDownloadMbps())
                .maxConnections(node.getMaxConnections())
                .cpuCores(node.getCpuCores())
                .ramMb(node.getRamMb())
                .deviceType(node.getDeviceType())
                .softwareVersion(node.getSoftwareVersion())
                .online(node.getOnline())
                .lastHeartbeat(node.getLastHeartbeat())
                .uptimePercentage(node.getUptimePercentage())
                .currentConnections(node.getCurrentConnections())
                .isMiningEnabled(node.getIsMiningEnabled())
                .totalBandwidthServedGb(node.getTotalBandwidthServedGb())
                .totalTokensEarned(node.getTotalTokensEarned())
                .createdAt(node.getCreatedAt())
                .build();
    }
}
