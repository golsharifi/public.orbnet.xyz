package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.OrbMeshHomeDevice;
import com.orbvpn.api.domain.entity.OrbMeshNode;
import com.orbvpn.api.domain.entity.OrbMeshPartner;
import com.orbvpn.api.domain.entity.OrbMeshTokenEarning;
import com.orbvpn.api.domain.enums.DeploymentType;
import com.orbvpn.api.domain.enums.PartnerTier;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.hybrid.OrbMeshNodeService;
import com.orbvpn.api.service.hybrid.OrbMeshPartnerService;
import com.orbvpn.api.service.hybrid.OrbMeshPartnerService.PartnerDashboard;
import com.orbvpn.api.repository.OrbMeshPartnerRepository;
import com.orbvpn.api.repository.OrbMeshNodeRepository;
import com.orbvpn.api.repository.OrbMeshTokenEarningRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HybridQueryResolver {

    private final OrbMeshPartnerService partnerService;
    private final OrbMeshNodeService nodeService;
    private final OrbMeshPartnerRepository partnerRepository;
    private final OrbMeshNodeRepository nodeRepository;
    private final OrbMeshTokenEarningRepository earningRepository;
    private final UserService userService;

    // ========== Partner Queries ==========

    @QueryMapping
    public PartnerDashboard partnerDashboard() {
        User user = userService.getUser();
        OrbMeshPartner partner = partnerRepository.findByContactEmail(user.getEmail())
                .orElse(null);
        if (partner == null) {
            log.warn("No partner found for user: {}", user.getEmail());
            return null;
        }
        return partnerService.getPartnerDashboard(partner.getId());
    }

    @QueryMapping
    public OrbMeshPartner myPartner() {
        User user = userService.getUser();
        return partnerRepository.findByContactEmail(user.getEmail()).orElse(null);
    }

    @QueryMapping
    public OrbMeshPartner partnerById(@Argument Long id) {
        return partnerService.getById(id);
    }

    // ========== Node Queries ==========

    @QueryMapping
    public List<OrbMeshNode> myNodes() {
        User user = userService.getUser();
        return nodeRepository.findAll(); // Simplified - add findByOwnerUser query
    }

    @QueryMapping
    public List<OrbMeshHomeDevice> myHomeDevices() {
        User user = userService.getUser();
        return nodeService.getHomeDevicesByUser(user);
    }

    @QueryMapping
    public OrbMeshNode nodeByUuid(@Argument String uuid) {
        return nodeService.getByUuid(uuid);
    }

    @QueryMapping
    public List<OrbMeshNode> onlineNodes(
            @Argument String deploymentType,
            @Argument String region) {
        if (deploymentType != null && region != null) {
            DeploymentType type = DeploymentType.valueOf(deploymentType.toUpperCase());
            return nodeService.getOnlineNodesByDeploymentType(type).stream()
                    .filter(n -> region.equals(n.getRegion()))
                    .toList();
        } else if (deploymentType != null) {
            DeploymentType type = DeploymentType.valueOf(deploymentType.toUpperCase());
            return nodeService.getOnlineNodesByDeploymentType(type);
        } else if (region != null) {
            return nodeService.getOnlineNodesByRegion(region);
        }
        return nodeService.getOnlineNodes();
    }

    // ========== Earnings Queries ==========

    @QueryMapping
    public Page<OrbMeshTokenEarning> myEarnings(
            @Argument Integer page,
            @Argument Integer size) {
        User user = userService.getUser();
        return earningRepository.findByUser(user, PageRequest.of(
                page != null ? page : 0,
                size != null ? size : 20));
    }

    @QueryMapping
    public Page<OrbMeshTokenEarning> partnerEarnings(
            @Argument Long partnerId,
            @Argument Integer page,
            @Argument Integer size) {
        OrbMeshPartner partner = partnerService.getById(partnerId);
        return earningRepository.findByPartner(partner, PageRequest.of(
                page != null ? page : 0,
                size != null ? size : 20));
    }

    // ========== Admin Queries ==========

    @Secured(ADMIN)
    @QueryMapping
    public HybridAdminStats adminHybridStats() {
        int totalPartners = (int) partnerRepository.count();
        int activePartners = partnerRepository.countActivePartners();
        int totalNodes = (int) nodeRepository.count();
        int onlineNodes = nodeService.countOnlineNodes();

        List<Object[]> deploymentStats = nodeService.getDeploymentTypeStatistics();
        List<Object[]> regionStats = nodeService.getRegionStatistics();

        BigDecimal totalBandwidth = partnerRepository.sumTotalBandwidth();
        BigDecimal totalTokens = partnerRepository.sumTotalTokensEarned();
        BigDecimal pendingPayouts = earningRepository.sumAllPending();

        return new HybridAdminStats(
                totalPartners,
                activePartners,
                totalNodes,
                onlineNodes,
                deploymentStats,
                regionStats,
                totalBandwidth != null ? totalBandwidth : BigDecimal.ZERO,
                totalTokens != null ? totalTokens : BigDecimal.ZERO,
                pendingPayouts != null ? pendingPayouts : BigDecimal.ZERO
        );
    }

    @Secured(ADMIN)
    @QueryMapping
    public Page<OrbMeshPartner> adminAllPartners(
            @Argument Integer page,
            @Argument Integer size,
            @Argument String tier) {
        PageRequest pageRequest = PageRequest.of(
                page != null ? page : 0,
                size != null ? size : 20);

        if (tier != null) {
            PartnerTier partnerTier = PartnerTier.valueOf(tier.toUpperCase());
            return partnerService.getPartnersByTier(partnerTier, pageRequest);
        }
        return partnerService.getAllPartners(pageRequest);
    }

    @Secured(ADMIN)
    @QueryMapping
    public Page<OrbMeshNode> adminAllNodes(
            @Argument Integer page,
            @Argument Integer size,
            @Argument String deploymentType,
            @Argument Boolean online) {
        PageRequest pageRequest = PageRequest.of(
                page != null ? page : 0,
                size != null ? size : 20);

        if (deploymentType != null) {
            DeploymentType type = DeploymentType.valueOf(deploymentType.toUpperCase());
            return nodeService.getNodesByDeploymentType(type, pageRequest);
        } else if (online != null) {
            return nodeService.getNodesByOnlineStatus(online, pageRequest);
        }
        return nodeService.getAllNodes(pageRequest);
    }

    @Secured(ADMIN)
    @QueryMapping
    public OrbMeshPartner adminPartnerById(@Argument Long id) {
        return partnerService.getById(id);
    }

    @Secured(ADMIN)
    @QueryMapping
    public OrbMeshNode adminNodeByUuid(@Argument String uuid) {
        return nodeService.getByUuid(uuid);
    }

    // Stats record
    public record HybridAdminStats(
            int totalPartners,
            int activePartners,
            int totalNodes,
            int onlineNodes,
            List<Object[]> nodesByDeploymentType,
            List<Object[]> nodesByRegion,
            BigDecimal totalBandwidthServedGb,
            BigDecimal totalTokensDistributed,
            BigDecimal pendingPayouts
    ) {}
}
