package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.UserDeviceView;
import com.orbvpn.api.service.UserDeviceService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.RESELLER;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class UserDeviceQueryResolver {
    private final UserDeviceService userDeviceService;

    @Secured(USER)
    @QueryMapping
    public List<UserDeviceView> getActiveDevices() {
        log.info("Fetching active devices for current user");
        try {
            List<UserDeviceView> devices = userDeviceService.getActiveDevices();
            log.info("Successfully retrieved {} active devices", devices.size());
            return devices;
        } catch (Exception e) {
            log.error("Error fetching active devices - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @QueryMapping
    public List<UserDeviceView> resellerGetActiveDevices(@Argument Integer userId) {
        log.info("Reseller fetching active devices for user: {}", userId);
        try {
            List<UserDeviceView> devices = userDeviceService.resellerGetActiveDevices(userId);
            log.info("Successfully retrieved {} active devices for user: {}", devices.size(), userId);
            return devices;
        } catch (Exception e) {
            log.error("Error fetching active devices for user: {} - Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }
}