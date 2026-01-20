package com.orbvpn.api.config;

import com.orbvpn.api.service.subscription.ProductGroupMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

@Configuration
@Slf4j
public class SubscriptionGroupConfig {
    @Bean
    public Map<String, Integer> groupMap() {
        Map<String, Integer> map = Map.of(
                "orb_basic_monthly", 21,
                "orb_premium_monthly", 22,
                "orb_family_monthly", 23,
                "orb_basic_yearly", 24,
                "orb_premium_yearly", 25,
                "orb_family_yearly", 26);
        log.info("Initialized subscription group mappings: {}", map);
        return map;
    }

    // Helper method for common mapping logic
    @Bean
    public ProductGroupMapper productGroupMapper(Map<String, Integer> groupMap) {
        return new ProductGroupMapper(groupMap);
    }
}
