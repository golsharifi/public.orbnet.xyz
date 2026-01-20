package com.orbvpn.api.service.subscription.handlers;

import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.exception.SubscriptionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionHandlerFactory {
    private final List<SubscriptionHandler> handlers;
    private final Map<GatewayName, SubscriptionHandler> handlerMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (SubscriptionHandler handler : handlers) {
            handlerMap.put(handler.getGatewayType(), handler);
            log.info("Registered subscription handler for gateway: {}", handler.getGatewayType());
        }
    }

    public SubscriptionHandler getHandler(GatewayName gateway) {
        SubscriptionHandler handler = handlerMap.get(gateway);
        if (handler == null) {
            throw new SubscriptionException("No handler found for gateway: " + gateway);
        }
        return handler;
    }
}