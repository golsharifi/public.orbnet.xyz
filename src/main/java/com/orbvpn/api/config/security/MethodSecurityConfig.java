package com.orbvpn.api.config.security;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.SecuredAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Configures method-level security to use role hierarchy.
 * This enables hierarchical RBAC where:
 * - ADMIN > RESELLER > USER
 * - @Secured("USER") will also accept RESELLER and ADMIN
 * - @Secured("RESELLER") will also accept ADMIN
 */
@Configuration
public class MethodSecurityConfig {

    /**
     * Creates a MethodSecurityExpressionHandler that uses the role hierarchy.
     * This is used by @PreAuthorize and @PostAuthorize annotations.
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    /**
     * Custom AuthorizationManager for @Secured annotation that supports role hierarchy.
     * This wraps the default SecuredAuthorizationManager and applies role hierarchy
     * to the authentication's authorities before checking.
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public Advisor securedAuthorizationManagerAdvisor(RoleHierarchy roleHierarchy) {
        SecuredAuthorizationManager delegate = new SecuredAuthorizationManager();

        AuthorizationManager<MethodInvocation> hierarchicalManager =
            (Supplier<Authentication> authentication, MethodInvocation invocation) -> {
                Authentication auth = authentication.get();
                if (auth == null) {
                    return delegate.check(authentication, invocation);
                }

                // Get reachable authorities based on role hierarchy
                Collection<? extends GrantedAuthority> reachableAuthorities =
                    roleHierarchy.getReachableGrantedAuthorities(auth.getAuthorities());

                // Create a wrapper authentication with expanded authorities
                Authentication expandedAuth = new Authentication() {
                    @Override
                    public Collection<? extends GrantedAuthority> getAuthorities() {
                        return reachableAuthorities;
                    }

                    @Override
                    public Object getCredentials() {
                        return auth.getCredentials();
                    }

                    @Override
                    public Object getDetails() {
                        return auth.getDetails();
                    }

                    @Override
                    public Object getPrincipal() {
                        return auth.getPrincipal();
                    }

                    @Override
                    public boolean isAuthenticated() {
                        return auth.isAuthenticated();
                    }

                    @Override
                    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
                        auth.setAuthenticated(isAuthenticated);
                    }

                    @Override
                    public String getName() {
                        return auth.getName();
                    }
                };

                return delegate.check(() -> expandedAuth, invocation);
            };

        return AuthorizationManagerBeforeMethodInterceptor.secured(hierarchicalManager);
    }
}
