package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.service.MiningServerService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MiningServerMutationResolver implements GraphQLMutationResolver {
    private final MiningServerService miningServerService;

    public MiningServerView enableMiningServer(Long serverId) {
        return miningServerService.enableMiningServer(serverId);
    }

    public MiningServerView disableMiningServer(Long serverId) {
        return miningServerService.disableMiningServer(serverId);
    }
}