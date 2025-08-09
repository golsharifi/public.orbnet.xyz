package com.orbvpn.api.config;

import graphql.ExceptionWhileDataFetching;
import graphql.GraphQLError;
import graphql.ErrorClassification;
import graphql.language.SourceLocation;
import graphql.kickstart.execution.error.GraphQLErrorHandler;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Component
@Primary
public class CustomGraphQLErrorHandler implements GraphQLErrorHandler {

    @Override
    public boolean errorsPresent(List<GraphQLError> errors) {
        return errors != null && !errors.isEmpty();
    }

    @Override
    public List<GraphQLError> processErrors(List<GraphQLError> errors) {
        return errors.stream()
                .map(this::getNormalizedError)
                .collect(Collectors.toList());
    }

    private GraphQLError getNormalizedError(GraphQLError error) {
        if (error instanceof ExceptionWhileDataFetching) {
            ExceptionWhileDataFetching unwrapped = (ExceptionWhileDataFetching) error;
            return new SimpleGraphQLError(unwrapped.getException().getMessage());
        }
        return error;
    }
}

class SimpleGraphQLError implements GraphQLError {
    private final String message;

    SimpleGraphQLError(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<Object> getPath() {
        return null;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return null;
    }

    @Override
    public ErrorClassification getErrorType() {
        return new ErrorClassification() {
            @Override
            public String toString() {
                return "INTERNAL_ERROR";
            }
        };
    }

    @Override
    public Map<String, Object> getExtensions() {
        Map<String, Object> extensions = new HashMap<>();
        extensions.put("errorType", "INTERNAL_ERROR");
        return extensions;
    }
}