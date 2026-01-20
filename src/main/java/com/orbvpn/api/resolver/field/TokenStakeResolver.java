package com.orbvpn.api.resolver.field;

import com.orbvpn.api.domain.entity.TokenStake;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class TokenStakeResolver {

    @SchemaMapping(typeName = "TokenStake", field = "userId")
    public Integer getUserId(TokenStake tokenStake) {
        return tokenStake.getUser() != null ? tokenStake.getUser().getId() : null;
    }
}