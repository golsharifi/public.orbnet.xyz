package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.MiningServerView;
import com.orbvpn.api.domain.dto.ServersByProtocol;
import com.orbvpn.api.domain.dto.ServerStats;
import com.orbvpn.api.domain.enums.SortType;
import com.orbvpn.api.domain.enums.ProtocolType;
import com.orbvpn.api.service.MiningServerService;
import com.orbvpn.api.service.user.UserContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class MiningServerQueryResolver {
    private final MiningServerService miningServerService;
    private final UserContextService userContextService;

    @QueryMapping
    public List<MiningServerView> miningServers() {
        return miningServerService.getMiningServers(userContextService.getCurrentUser());
    }

    @QueryMapping
    public List<MiningServerView> miningServersByProtocol(@Argument ProtocolType protocol) {
        return miningServerService.getServersByProtocol(protocol, userContextService.getCurrentUser());
    }

    @QueryMapping
    public ServersByProtocol allServersByProtocol(@Argument SortType sortBy, @Argument Boolean ascending) {
        return miningServerService.getAllServersByProtocol(
                sortBy,
                ascending,
                userContextService.getCurrentUser());
    }

    @QueryMapping
    public ServerStats serverStats() {
        return miningServerService.getServerStats();
    }
}