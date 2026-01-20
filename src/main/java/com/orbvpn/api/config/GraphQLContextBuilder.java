package com.orbvpn.api.config;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class GraphQLContextBuilder implements WebGraphQlInterceptor {

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        log.debug("Processing GraphQL request: {}", request.getDocument());

        String operationName = request.getOperationName();
        log.debug("Operation name: {}", operationName);

        return chain.next(request)
                .map(response -> {
                    if (!response.getErrors().isEmpty()) {
                        log.error("GraphQL errors: {}", response.getErrors());
                        // Convert errors to more user-friendly format if needed
                    }
                    return response;
                });
    }
}