package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.MiningRewardResult;
import com.orbvpn.api.domain.dto.MiningActivityView;
import com.orbvpn.api.domain.dto.MiningSettingsInput;
import com.orbvpn.api.domain.dto.MiningSettingsView;
import com.orbvpn.api.domain.dto.WithdrawResult;
import com.orbvpn.api.service.MiningRewardService;
import com.orbvpn.api.service.MiningServerService;
import com.orbvpn.api.service.mining.MiningActivityService;
import com.orbvpn.api.service.user.UserContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import java.math.BigDecimal;

@Controller
@RequiredArgsConstructor
public class MiningMutationResolver {
    private final MiningServerService miningServerService;
    private final MiningRewardService miningRewardService;
    private final UserContextService userContextService;
    private final MiningActivityService miningActivityService;

    @MutationMapping
    public WithdrawResult withdrawTokens(@Argument BigDecimal amount) {
        return miningRewardService.withdrawTokens(
                amount,
                userContextService.getCurrentUser());
    }

    @MutationMapping
    public MiningActivityView startMining(@Argument Long serverId) {
        return miningActivityService.startMining(
                userContextService.getCurrentUser(),
                serverId);
    }

    @MutationMapping
    public MiningActivityView stopMining(@Argument Long serverId) {
        return miningActivityService.stopMining(
                userContextService.getCurrentUser(),
                serverId);
    }

    @MutationMapping
    public MiningRewardResult claimMiningRewards(@Argument Long serverId) {
        return miningRewardService.claimRewards(
                serverId,
                userContextService.getCurrentUser());
    }

    @MutationMapping
    public MiningSettingsView updateMiningSettings(@Argument MiningSettingsInput input) {
        return miningServerService.updateMiningSettings(
                input,
                userContextService.getCurrentUser());
    }

    @MutationMapping
    public MiningRewardResult claimRewards() {
        return miningRewardService.claimRewards(
                userContextService.getCurrentUser());
    }
}