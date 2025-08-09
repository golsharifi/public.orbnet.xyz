package com.orbvpn.api.resolver.field;

import com.orbvpn.api.domain.entity.TokenStake;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

@Component
public class TokenStakeResolver implements GraphQLResolver<TokenStake> {

    public Integer getUserId(TokenStake tokenStake) {
        return tokenStake.getUser() != null ? tokenStake.getUser().getId() : null;
    }
}