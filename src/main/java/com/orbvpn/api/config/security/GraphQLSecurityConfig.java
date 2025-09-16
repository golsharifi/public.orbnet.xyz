package com.orbvpn.api.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.server.WebGraphQlInterceptor;

import com.orbvpn.api.resolver.mutation.UserMutation;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

@Configuration
@Slf4j
public class GraphQLSecurityConfig {

    // @Bean
    // public WebGraphQlInterceptor securityInterceptor() {
    // return (webInput, interceptorChain) -> {
    // String operationName = webInput.getOperationName();
    // if (isUnsecuredOperation(operationName)) {
    // log.debug("Skipping authentication for unsecured operation: {}",
    // operationName);
    // return interceptorChain.next(webInput); // Skip security checks
    // }

    // return interceptorChain.next(webInput);
    // };
    // }

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