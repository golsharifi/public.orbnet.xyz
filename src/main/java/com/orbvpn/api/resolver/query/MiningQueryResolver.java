package com.orbvpn.api.resolver.query;

import com.orbvpn.api.service.MiningRewardService;
import com.orbvpn.api.service.mining.ServerMetricsService;
import com.orbvpn.api.service.mining.MiningActivityService;
import com.orbvpn.api.service.user.UserContextService;
import com.orbvpn.api.domain.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class MiningQueryResolver {
    private final MiningActivityService miningActivityService;
    private final MiningRewardService miningRewardService;
    private final ServerMetricsService serverMetricsService;
    private final UserContextService userContextService;

    @QueryMapping
    public MiningActivityView miningActivity() {
        return miningActivityService.getCurrentActivity(userContextService.getCurrentUser());
    }

    @QueryMapping
    public List<MiningRewardView> miningRewards(@Argument LocalDateTime from, @Argument LocalDateTime to) {
        return miningRewardService.getRewards(
                userContextService.getCurrentUser(),
                from,
                to);
    }

    @QueryMapping
    public ServerMiningMetrics serverMiningMetrics(@Argument Long serverId) {
        return serverMetricsService.getServerMetrics(serverId);
    }

    @QueryMapping
    public MiningStats miningStats() {
        return miningRewardService.getMiningStats(userContextService.getCurrentUser());
    }
}