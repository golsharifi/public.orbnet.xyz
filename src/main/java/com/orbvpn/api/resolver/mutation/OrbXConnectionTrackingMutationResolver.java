package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.service.OrbXConnectionTrackingService;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * GraphQL Mutation Resolver for connection tracking.
 * These mutations are called by OrbX servers (not clients) to report
 * connection starts and ends for multi-login tracking.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class OrbXConnectionTrackingMutationResolver {

    private final OrbXConnectionTrackingService connectionTrackingService;

    /**
     * Report a connection start from OrbX server.
     * Called when a user successfully establishes a VPN connection.
     */
    @MutationMapping
    public Map<String, Object> reportOrbXConnectionStart(@Argument Map<String, Object> input) {
        Map<String, Object> result = new HashMap<>();

        try {
            Integer userId = (Integer) input.get("userId");
            Long serverId = Long.parseLong(input.get("serverId").toString());
            String sessionId = (String) input.get("sessionId");
            String protocol = (String) input.get("protocol");
            String vpnProtocol = (String) input.get("vpnProtocol");
            String clientIp = (String) input.get("clientIp");
            String clientPlatform = (String) input.get("clientPlatform");

            log.info("Received connection start report: user={}, server={}, session={}, protocol={}, vpnProtocol={}",
                    userId, serverId, sessionId, protocol, vpnProtocol);

            Long connectionId = connectionTrackingService.recordConnectionStart(
                    userId, serverId, sessionId, protocol, vpnProtocol, clientIp, clientPlatform);

            if (connectionId != null) {
                result.put("success", true);
                result.put("message", "Connection start recorded");
                result.put("connectionId", connectionId);
            } else {
                result.put("success", false);
                result.put("message", "Failed to record connection start");
                result.put("connectionId", null);
            }

        } catch (Exception e) {
            log.error("Error processing connection start report: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Error: " + e.getMessage());
            result.put("connectionId", null);
        }

        return result;
    }

    /**
     * Report a connection end from OrbX server.
     * Called when a user disconnects from VPN.
     */
    @MutationMapping
    public Map<String, Object> reportOrbXConnectionEnd(@Argument Map<String, Object> input) {
        Map<String, Object> result = new HashMap<>();

        try {
            String sessionId = (String) input.get("sessionId");
            Long bytesSent = input.get("bytesSent") != null
                    ? ((Number) input.get("bytesSent")).longValue()
                    : 0L;
            Long bytesReceived = input.get("bytesReceived") != null
                    ? ((Number) input.get("bytesReceived")).longValue()
                    : 0L;
            String disconnectReason = (String) input.get("disconnectReason");

            log.info("Received connection end report: session={}, sent={}, received={}, reason={}",
                    sessionId, bytesSent, bytesReceived, disconnectReason);

            boolean success = connectionTrackingService.recordConnectionEnd(
                    sessionId, bytesSent, bytesReceived, disconnectReason);

            result.put("success", success);
            result.put("message", success ? "Connection end recorded" : "Failed to record connection end");

        } catch (Exception e) {
            log.error("Error processing connection end report: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Error: " + e.getMessage());
        }

        return result;
    }
}
