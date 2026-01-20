package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.BridgeConnectionView;
import com.orbvpn.api.domain.dto.BridgeSettingsView;
import com.orbvpn.api.service.BridgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import java.util.Map;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class BridgeMutationResolver {

    private final BridgeService bridgeService;

    @Secured(USER)
    @MutationMapping
    public BridgeSettingsView setBridgeEnabled(@Argument Boolean enabled) {
        log.info("Setting bridge enabled: {}", enabled);
        try {
            BridgeSettingsView settings = bridgeService.setBridgeEnabled(enabled);
            log.info("Successfully set bridge enabled to: {}", enabled);
            return settings;
        } catch (Exception e) {
            log.error("Error setting bridge enabled to {} - Error: {}", enabled, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @MutationMapping
    public BridgeSettingsView selectBridge(@Argument Long bridgeId) {
        log.info("Selecting bridge: {}", bridgeId);
        try {
            BridgeSettingsView settings = bridgeService.selectBridge(bridgeId);
            log.info("Successfully selected bridge: {}", bridgeId);
            return settings;
        } catch (Exception e) {
            log.error("Error selecting bridge {} - Error: {}", bridgeId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @MutationMapping
    public BridgeSettingsView setAutoBridge(@Argument Boolean enabled) {
        log.info("Setting auto bridge: {}", enabled);
        try {
            BridgeSettingsView settings = bridgeService.setAutoBridge(enabled);
            log.info("Successfully set auto bridge to: {}", enabled);
            return settings;
        } catch (Exception e) {
            log.error("Error setting auto bridge to {} - Error: {}", enabled, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @MutationMapping
    public BridgeConnectionView logBridgeConnection(@Argument Map<String, Object> input) {
        Long bridgeServerId = Long.valueOf(input.get("bridgeServerId").toString());
        Long exitServerId = Long.valueOf(input.get("exitServerId").toString());
        String protocol = (String) input.get("protocol");

        log.info("Logging bridge connection - bridge: {}, exit: {}, protocol: {}",
                bridgeServerId, exitServerId, protocol);
        try {
            BridgeConnectionView connection = bridgeService.logBridgeConnection(
                    bridgeServerId, exitServerId, protocol);
            log.info("Successfully logged bridge connection");
            return connection;
        } catch (Exception e) {
            log.error("Error logging bridge connection - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @MutationMapping
    public BridgeConnectionView logBridgeDisconnection(@Argument Map<String, Object> input) {
        Long connectionId = Long.valueOf(input.get("connectionId").toString());
        Long bytesSent = input.get("bytesSent") != null ?
                Long.valueOf(input.get("bytesSent").toString()) : null;
        Long bytesReceived = input.get("bytesReceived") != null ?
                Long.valueOf(input.get("bytesReceived").toString()) : null;

        log.info("Logging bridge disconnection - connectionId: {}", connectionId);
        try {
            BridgeConnectionView connection = bridgeService.logBridgeDisconnection(
                    connectionId, bytesSent, bytesReceived);
            log.info("Successfully logged bridge disconnection");
            return connection;
        } catch (Exception e) {
            log.error("Error logging bridge disconnection - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}
