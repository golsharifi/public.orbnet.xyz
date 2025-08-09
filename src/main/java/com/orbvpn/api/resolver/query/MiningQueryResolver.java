package com.orbvpn.api.resolver.query;

import com.orbvpn.api.service.MiningRewardService;
import com.orbvpn.api.service.mining.ServerMetricsService;
import com.orbvpn.api.service.mining.MiningActivityService;
import com.orbvpn.api.service.user.UserContextService;

import com.orbvpn.api.domain.dto.*;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MiningQueryResolver implements GraphQLQueryResolver {
    private final MiningActivityService miningActivityService;
    private final MiningRewardService miningRewardService;
    private final ServerMetricsService serverMetricsService;
    private final UserContextService userContextService;

    public MiningActivityView miningActivity() {
        return miningActivityService.getCurrentActivity(userContextService.getCurrentUser());
    }

    public List<MiningRewardView> miningRewards(LocalDateTime from, LocalDateTime to) {
        return miningRewardService.getRewards(
                userContextService.getCurrentUser(),
                from,
                to);
    }

    public ServerMiningMetrics serverMiningMetrics(Long serverId) {
        return serverMetricsService.getServerMetrics(serverId);
    }

    public MiningStats miningStats() {
        return miningRewardService.getMiningStats(userContextService.getCurrentUser());
    }
}