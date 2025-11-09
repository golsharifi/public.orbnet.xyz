package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.service.IPService;
import com.orbvpn.api.domain.entity.Blacklist;
import com.orbvpn.api.domain.entity.Whitelist;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class IPListMutationResolver {

    private final IPService ipService;

    @Secured(ADMIN)
    @MutationMapping
    public Whitelist addToWhitelist(
            @Argument @Valid @NotBlank(message = "IP address is required") String ipAddress) {
        log.info("Adding IP to whitelist: {}", ipAddress);
        try {
            return ipService.addToWhitelist(ipAddress);
        } catch (Exception e) {
            log.error("Error adding IP to whitelist - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean removeFromWhitelist(
            @Argument @Valid @NotBlank(message = "IP address is required") String ipAddress) {
        log.info("Removing IP from whitelist: {}", ipAddress);
        try {
            return ipService.removeFromWhitelist(ipAddress);
        } catch (Exception e) {
            log.error("Error removing IP from whitelist - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public Blacklist addToBlacklist(
            @Argument @Valid @NotBlank(message = "IP address is required") String ipAddress) {
        log.info("Adding IP to blacklist: {}", ipAddress);
        try {
            return ipService.addToBlacklist(ipAddress);
        } catch (Exception e) {
            log.error("Error adding IP to blacklist - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean removeFromBlacklist(
            @Argument @Valid @NotBlank(message = "IP address is required") String ipAddress) {
        log.info("Removing IP from blacklist: {}", ipAddress);
        try {
            return ipService.removeFromBlacklist(ipAddress);
        } catch (Exception e) {
            log.error("Error removing IP from blacklist - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}