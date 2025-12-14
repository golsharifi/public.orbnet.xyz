package com.orbvpn.api.resolver.query;

import com.orbvpn.api.service.IPService;
import com.orbvpn.api.domain.entity.Blacklist;
import com.orbvpn.api.domain.entity.Whitelist;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class IPQueryResolver {
    private final IPService ipService;

    @Secured(ADMIN)
    @QueryMapping
    public List<Blacklist> getBlacklistedIPs() {
        log.info("Fetching blacklisted IPs");
        try {
            List<Blacklist> blacklist = ipService.getBlacklistedIPs();
            log.info("Successfully retrieved {} blacklisted IPs", blacklist.size());
            return blacklist;
        } catch (Exception e) {
            log.error("Error fetching blacklisted IPs - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<Whitelist> getWhitelistedIPs() {
        log.info("Fetching whitelisted IPs");
        try {
            List<Whitelist> whitelist = ipService.getWhitelistedIPs();
            log.info("Successfully retrieved {} whitelisted IPs", whitelist.size());
            return whitelist;
        } catch (Exception e) {
            log.error("Error fetching whitelisted IPs - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}