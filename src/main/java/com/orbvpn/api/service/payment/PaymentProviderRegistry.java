package com.orbvpn.api.service.payment;

import com.orbvpn.api.exception.PaymentException;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Service
public class PaymentProviderRegistry {
    private final Map<String, PaymentProvider> providers = new HashMap<>();
    private final List<PaymentProvider> availableProviders;

    public PaymentProviderRegistry(List<PaymentProvider> availableProviders) {
        this.availableProviders = availableProviders;
    }

    @PostConstruct
    public void init() {
        for (PaymentProvider provider : availableProviders) {
            providers.put(provider.getName(), provider);
        }
    }

    public PaymentProvider getProvider(String name) {
        PaymentProvider provider = providers.get(name);
        if (provider == null) {
            throw new PaymentException("Payment provider not found: " + name);
        }
        return provider;
    }

    public List<String> getAvailableProviders() {
        return List.copyOf(providers.keySet());
    }
}