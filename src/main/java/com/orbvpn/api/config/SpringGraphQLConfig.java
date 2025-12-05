package com.orbvpn.api.config;

import com.orbvpn.api.exception.BadCredentialsException;
import graphql.scalars.ExtendedScalars;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.server.WebGraphQlInterceptor;

import reactor.core.publisher.Mono;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.springframework.graphql.execution.ErrorType;
import com.orbvpn.api.scalar.LocalDateCoercing;
import com.orbvpn.api.scalar.LocalDateTimeCoercing;
import com.orbvpn.api.scalar.UploadCoercing;
import graphql.schema.GraphQLScalarType;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SpringGraphQLConfig {

    @Bean
    public GraphQLScalarType uploadScalar() {
        return GraphQLScalarType.newScalar()
                .name("Upload")
                .description("File upload scalar")
                .coercing(new UploadCoercing())
                .build();
    }

    @Bean
    public GraphQLScalarType localDateTimeScalar() {
        return GraphQLScalarType.newScalar()
                .name("LocalDateTime")
                .description("Java 8 LocalDateTime as scalar")
                .coercing(new LocalDateTimeCoercing())
                .build();
    }

    @Bean
    public GraphQLScalarType localDateScalar() {
        return GraphQLScalarType.newScalar()
                .name("LocalDate")
                .description("Java 8 LocalDate as scalar")
                .coercing(new LocalDateCoercing())
                .build();
    }

    @Bean
    public WebGraphQlInterceptor graphiqlHeaderInterceptor() {
        return (webInput, interceptorChain) -> {
            // Log the request for debugging
            String path = webInput.getUri().getPath();
            if (path.contains("graphiql")) {
                log.debug("GraphiQL request: {}", path);
            }

            // Continue with the chain
            return interceptorChain.next(webInput);
        };
    }

    @Bean
    public GraphQLScalarType bigDecimalScalar() {
        return ExtendedScalars.GraphQLBigDecimal;
    }

    @Bean
    public GraphQLScalarType bigIntegerScalar() {
        return ExtendedScalars.GraphQLBigInteger;
    }

    @Bean
    public GraphQLScalarType jsonScalar() {
        return ExtendedScalars.Json;
    }

    @Bean
    public GraphQLScalarType dateScalar() {
        return ExtendedScalars.Date;
    }

    @Bean
    public GraphQLScalarType dateTimeScalar() {
        return ExtendedScalars.DateTime;
    }

    @Bean
    public GraphQLScalarType longScalar() {
        return ExtendedScalars.GraphQLLong;
    }

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> {
            try {
                wiringBuilder
                        // Register all scalars with explicit names to match your GraphQL schema
                        .scalar(jsonScalar()) // JSON scalar
                        .scalar(localDateTimeScalar()) // LocalDateTime scalar
                        .scalar(localDateScalar()) // LocalDate scalar
                        .scalar(dateScalar()) // Date scalar
                        .scalar(dateTimeScalar()) // DateTime scalar
                        .scalar(bigDecimalScalar()) // BigDecimal scalar
                        .scalar(bigIntegerScalar()) // BigInteger scalar
                        .scalar(uploadScalar()) // Upload scalar
                        .scalar(longScalar()); // Long scalar

                log.info(
                        "Successfully configured GraphQL scalar types: JSON, LocalDateTime, LocalDate, Date, DateTime, BigDecimal, BigInteger, Upload, Long");
            } catch (Exception e) {
                log.error("Error configuring GraphQL scalars: {}", e.getMessage(), e);
                throw e;
            }
        };
    }

    @Bean
    public DataFetcherExceptionResolver exceptionResolver() {
        return (ex, env) -> {
            log.error("GraphQL error occurred", ex);

            if (ex instanceof BadCredentialsException) {
                GraphQLError error = GraphqlErrorBuilder.newError()
                        .message(ex.getMessage())
                        .errorType(ErrorType.UNAUTHORIZED)
                        .path(env.getExecutionStepInfo().getPath())
                        .location(env.getField().getSourceLocation())
                        .extensions(Map.of("code", "UNAUTHORIZED"))
                        .build();

                return Mono.just(Collections.singletonList(error));
            }

            GraphQLError error = GraphqlErrorBuilder.newError()
                    .message(ex.getMessage())
                    .path(env.getExecutionStepInfo().getPath())
                    .location(env.getField().getSourceLocation())
                    .extensions(Map.of("code", "INTERNAL_ERROR"))
                    .build();

            return Mono.just(Collections.singletonList(error));
        };
    }
}