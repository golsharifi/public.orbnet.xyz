package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.service.MiningServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class MiningServerMutationResolver {
    private final MiningServerService miningServerService;

    @MutationMapping
    public MiningServerView enableMiningServer(@Argument Long serverId) {
        return miningServerService.enableMiningServer(serverId);
    }

    @MutationMapping
    public MiningServerView disableMiningServer(@Argument Long serverId) {
        return miningServerService.disableMiningServer(serverId);
    }
}