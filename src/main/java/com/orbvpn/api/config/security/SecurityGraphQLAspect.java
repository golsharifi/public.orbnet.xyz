package com.orbvpn.api.config.security;

import com.orbvpn.api.exception.UnauthenticatedAccessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@Order(1)
public class SecurityGraphQLAspect {

    @Around("allGraphQLResolverMethods() && isDefinedInApplication()")
    public Object doSecurityCheck(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getName();

        // Allow login mutation to proceed without authentication
        if (methodName.equals("login") || method.isAnnotationPresent(Unsecured.class)) {
            return joinPoint.proceed();
        }

        // For all other methods, check authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthenticatedAccessException("Authentication required");
        }

        return joinPoint.proceed();
    }

    @Pointcut("@within(org.springframework.stereotype.Controller)")
    private void allGraphQLResolverMethods() {
    }

    @Pointcut("within(com.orbvpn.api..*)")
    private void isDefinedInApplication() {
    }
}