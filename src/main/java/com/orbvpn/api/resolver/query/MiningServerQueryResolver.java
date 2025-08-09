package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.MiningServerView;
import com.orbvpn.api.domain.dto.ServersByProtocol;
import com.orbvpn.api.domain.dto.ServerStats;
import com.orbvpn.api.domain.enums.SortType;
import com.orbvpn.api.domain.enums.ProtocolType;
import com.orbvpn.api.service.MiningServerService;
import com.orbvpn.api.service.user.UserContextService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MiningServerQueryResolver implements GraphQLQueryResolver {
    private final MiningServerService miningServerService;
    private final UserContextService userContextService;

    public List<MiningServerView> miningServers() {
        return miningServerService.getMiningServers(userContextService.getCurrentUser());
    }

    public List<MiningServerView> miningServersByProtocol(ProtocolType protocol) {
        return miningServerService.getServersByProtocol(protocol, userContextService.getCurrentUser());
    }

    public ServersByProtocol allServersByProtocol(SortType sortBy, Boolean ascending) {
        return miningServerService.getAllServersByProtocol(
                sortBy,
                ascending,
                userContextService.getCurrentUser());
    }

    public ServerStats serverStats() {
        return miningServerService.getServerStats();
    }
}
