package com.orbvpn.api.service.subscription;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class SubscriptionTrackingAspect {

    @Around("execution(* com.orbvpn.api.service.payment.*.*(..)) && " +
            "@annotation(com.orbvpn.api.annotation.TrackSubscription)")
    public Object trackSubscriptionOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        try {
            log.info("Starting subscription operation: {}.{}", className, methodName);
            Object result = joinPoint.proceed();
            log.info("Successfully completed subscription operation: {}.{}",
                    className, methodName);
            return result;
        } catch (Exception e) {
            log.error("Error in subscription operation: {}.{} - Error: {}",
                    className, methodName, e.getMessage(), e);
            throw e;
        }
    }
}
