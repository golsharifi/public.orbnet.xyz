package com.orbvpn.api.config.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.orbvpn.api.resolver.mutation.UserMutation;

import java.lang.reflect.Method;

import reactor.core.publisher.Mono;

@Component
@Slf4j
public class GraphQLAuthenticationInterceptor implements WebGraphQlInterceptor {

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        String operationName = request.getOperationName();
        log.debug("Processing GraphQL operation: {}", operationName);

        log.debug("Processing operation: {}", operationName);
        if (isUnsecuredOperation(operationName)) {
            log.debug("Skipping authentication for unsecured operation: {}", operationName);
            return chain.next(request); // Skip security checks for unsecured operations
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            log.debug("Anonymous authentication for operation: {}", operationName);
        } else {
            log.debug("Authenticating operation: {}", operationName);
            log.debug("Authenticated request for user: {} on operation: {}", authentication.getName(), operationName);
        }

        return chain.next(request)
                .map(response -> {
                    if (!response.getErrors().isEmpty()) {
                        log.error("GraphQL errors in response for operation {}: {}", operationName,
                                response.getErrors());
                    }
                    return response;
                });
    }

    private boolean isUnsecuredOperation(String operationName) {
        try {
            Method[] methods = UserMutation.class.getDeclaredMethods();
            for (Method method : methods) {
                if (method.getName().equalsIgnoreCase(operationName) && method.isAnnotationPresent(Unsecured.class)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Error checking unsecured operation", e);
        }
        return false;
    }
}