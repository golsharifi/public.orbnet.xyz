package com.orbvpn.api.exception.handler;

import com.orbvpn.api.exception.BadCredentialsException;
import com.orbvpn.api.exception.BadRequestException;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.exception.UnauthenticatedAccessException;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class GraphQLExceptionHandler extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        log.error("GraphQL error occurred: {}", ex.getMessage(), ex);

        if (ex instanceof BadCredentialsException) {
            return createError(ex, env, "UNAUTHORIZED", "Authentication failed");
        }

        if (ex instanceof UnauthenticatedAccessException) {
            return createError(ex, env, "UNAUTHENTICATED", "Please login to access this resource");
        }

        // Handle Spring Security exceptions
        if (ex instanceof AccessDeniedException) {
            return createError(ex, env, "FORBIDDEN", "Access denied: insufficient permissions");
        }

        if (ex instanceof InsufficientAuthenticationException) {
            return createError(ex, env, "UNAUTHENTICATED", "Authentication required");
        }

        if (ex instanceof BadRequestException) {
            return createError(ex, env, "BAD_REQUEST", ex.getMessage());
        }

        if (ex instanceof NotFoundException) {
            return createError(ex, env, "NOT_FOUND", ex.getMessage());
        }

        if (ex instanceof GraphQLException) {
            return createError(ex, env, "GRAPHQL_ERROR", ex.getMessage());
        }

        if (ex instanceof ConstraintViolationException) {
            return GraphQLError.newError()
                    .message(ex.getMessage())
                    .path(env.getExecutionStepInfo().getPath())
                    .location(env.getField().getSourceLocation())
                    .extensions(Map.of(
                            "classification", "ValidationError",
                            "code", "VALIDATION_ERROR"
                    ))
                    .build();
        }

        // Generic handler for unhandled exceptions
        log.error("Unhandled exception in GraphQL request", ex);
        return createError(ex, env, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private GraphQLError createError(Throwable ex, DataFetchingEnvironment env, String errorType,
                                     String defaultMessage) {
        String message = ex.getMessage() != null ? ex.getMessage() : defaultMessage;

        Map<String, Object> extensions = new HashMap<>();
        extensions.put("classification", errorType);
        extensions.put("timestamp", System.currentTimeMillis());

        if (env.getField() != null && env.getField().getName() != null) {
            extensions.put("fieldName", env.getField().getName());
        }

        return GraphQLError.newError()
                .message(message)
                .path(env.getExecutionStepInfo().getPath())
                .location(env.getField().getSourceLocation())
                .extensions(extensions)
                .build();
    }

    public GraphQLError resolveToSingleError(Throwable exception) {
        log.error("Generic GraphQL error: {}", exception.getMessage(), exception);
        return GraphqlErrorBuilder.newError()
                .message(exception.getMessage())
                .extensions(getExtensions("INTERNAL_ERROR"))
                .build();
    }

    private Map<String, Object> getExtensions(String errorType) {
        Map<String, Object> extensions = new HashMap<>();
        extensions.put("classification", errorType);
        extensions.put("timestamp", System.currentTimeMillis());
        return extensions;
    }
}