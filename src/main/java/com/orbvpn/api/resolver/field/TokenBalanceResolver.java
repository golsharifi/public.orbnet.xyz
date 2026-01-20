package com.orbvpn.api.resolver.field;

import com.orbvpn.api.domain.entity.TokenBalance;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class TokenBalanceResolver {

    @SchemaMapping(typeName = "TokenBalance", field = "userId")
    public Integer getUserId(TokenBalance tokenBalance) {
        return tokenBalance.getUser() != null ? tokenBalance.getUser().getId() : null;
    }
}