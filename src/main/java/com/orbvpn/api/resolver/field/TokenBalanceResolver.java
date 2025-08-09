package com.orbvpn.api.resolver.field;

import com.orbvpn.api.domain.entity.TokenBalance;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

@Component
public class TokenBalanceResolver implements GraphQLResolver<TokenBalance> {

    public Integer getUserId(TokenBalance tokenBalance) {
        return tokenBalance.getUser() != null ? tokenBalance.getUser().getId() : null;
    }
}