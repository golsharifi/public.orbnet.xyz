package com.orbvpn.api.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import com.orbvpn.api.filter.RateLimitFilter;
import com.orbvpn.api.filter.UserRateLimiter;
import com.orbvpn.api.service.IPService;
import com.orbvpn.api.service.TokenRateLimiterService;
import com.orbvpn.api.repository.BlacklistRepository;
import com.orbvpn.api.repository.WhitelistRepository;

@Configuration
public class FilterConfig {

    @Autowired
    private BlacklistRepository blacklistRepository;

    @Autowired
    private WhitelistRepository whitelistRepository;

    @Autowired
    private IPService ipService;

    @Autowired
    private RateLimitProperties rateLimitProperties;

    @Bean
    public UserRateLimiter userRateLimiter() {
        return new UserRateLimiter(ipService, rateLimitProperties);
    }

    @Bean
    public TokenRateLimiterService tokenRateLimiterService() {
        return new TokenRateLimiterService(rateLimitProperties);
    }

    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter();
    }

    @Bean
    public IPService ipService() {
        return new IPService(blacklistRepository, whitelistRepository);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> loggingFilter() {
        FilterRegistrationBean<RateLimitFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(rateLimitFilter());
        registrationBean.addUrlPatterns("/graphql");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE - 1);
        return registrationBean;
    }
}