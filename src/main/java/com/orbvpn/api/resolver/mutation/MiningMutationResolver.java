package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.MiningRewardResult;
import com.orbvpn.api.domain.dto.MiningServerView;
import com.orbvpn.api.domain.dto.MiningActivityView;
import com.orbvpn.api.domain.dto.MiningSettingsInput;
import com.orbvpn.api.domain.dto.MiningSettingsView;
import com.orbvpn.api.domain.dto.WithdrawResult;
import com.orbvpn.api.service.MiningRewardService;
import com.orbvpn.api.service.MiningServerService;
import com.orbvpn.api.service.mining.MiningActivityService;
import com.orbvpn.api.service.user.UserContextService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class MiningMutationResolver implements GraphQLMutationResolver {
    private final MiningServerService miningServerService;
    private final MiningRewardService miningRewardService;
    private final UserContextService userContextService;
    private final MiningActivityService miningActivityService;

    public MiningServerView enableMiningServer(Long serverId) {
        return miningServerService.enableMiningServer(serverId);
    }

    public MiningServerView disableMiningServer(Long serverId) {
        return miningServerService.disableMiningServer(serverId);
    }

    public WithdrawResult withdrawTokens(BigDecimal amount) {
        return miningRewardService.withdrawTokens(
                amount,
                userContextService.getCurrentUser());
    }

    public MiningActivityView startMining(Long serverId) {
        return miningActivityService.startMining(
                userContextService.getCurrentUser(),
                serverId);
    }

    public MiningActivityView stopMining(Long serverId) {
        return miningActivityService.stopMining(
                userContextService.getCurrentUser(),
                serverId);
    }

    public MiningRewardResult claimMiningRewards(Long serverId) {
        return miningRewardService.claimRewards(
                serverId,
                userContextService.getCurrentUser());
    }

    public MiningSettingsView updateMiningSettings(MiningSettingsInput input) {
        return miningServerService.updateMiningSettings(
                input,
                userContextService.getCurrentUser());
    }

    public MiningRewardResult claimRewards() {
        return miningRewardService.claimRewards(
                userContextService.getCurrentUser());
    }
}
