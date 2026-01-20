package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.service.OrbMeshConnectionTrackingService;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GraphQL Mutation Resolver for connection tracking.
 * These mutations are called by OrbMesh servers (not clients) to report
 * connection starts and ends for multi-login tracking.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class OrbMeshConnectionTrackingMutationResolver {

    private final OrbMeshConnectionTrackingService connectionTrackingService;

    /**
     * Report a connection start from OrbMesh server.
     * Called when a user successfully establishes a VPN connection.
     */
    @MutationMapping
    public Map<String, Object> reportOrbMeshConnectionStart(@Argument Map<String, Object> input) {
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
     * Report a connection end from OrbMesh server.
     * Called when a user disconnects from VPN.
     */
    @MutationMapping
    public Map<String, Object> reportOrbMeshConnectionEnd(@Argument Map<String, Object> input) {
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

    /**
     * Report a batch of connection starts from OrbMesh server.
     * Called for efficiency when multiple connections occur in a short time.
     */
    @MutationMapping("reportOrbMeshConnectionBatch")
    @SuppressWarnings("unchecked")
    public Map<String, Object> reportOrbMeshConnectionBatch(@Argument("events") List<Map<String, Object>> events) {
        Map<String, Object> result = new HashMap<>();
        int processedCount = 0;
        int failedCount = 0;

        log.info("Received batch connection start report with {} events", events.size());

        for (Map<String, Object> input : events) {
            try {
                Integer userId = (Integer) input.get("userId");
                Long serverId = Long.parseLong(input.get("serverId").toString());
                String sessionId = (String) input.get("sessionId");
                String protocol = (String) input.get("protocol");
                String vpnProtocol = (String) input.get("vpnProtocol");
                String clientIp = (String) input.get("clientIp");
                String clientPlatform = (String) input.get("clientPlatform");

                Long connectionId = connectionTrackingService.recordConnectionStart(
                        userId, serverId, sessionId, protocol, vpnProtocol, clientIp, clientPlatform);

                if (connectionId != null) {
                    processedCount++;
                } else {
                    failedCount++;
                }

            } catch (Exception e) {
                log.error("Error processing batch connection start event: {}", e.getMessage());
                failedCount++;
            }
        }

        log.info("Batch connection start completed: processed={}, failed={}", processedCount, failedCount);

        result.put("success", failedCount == 0);
        result.put("message", String.format("Processed %d events, %d failed", processedCount, failedCount));
        result.put("processedCount", processedCount);
        result.put("failedCount", failedCount);

        return result;
    }

    /**
     * Report a batch of connection ends from OrbMesh server.
     * Called for efficiency when multiple disconnections occur in a short time.
     */
    @MutationMapping("reportOrbMeshConnectionEndBatch")
    @SuppressWarnings("unchecked")
    public Map<String, Object> reportOrbMeshConnectionEndBatch(@Argument("events") List<Map<String, Object>> events) {
        Map<String, Object> result = new HashMap<>();
        int processedCount = 0;
        int failedCount = 0;

        log.info("Received batch connection end report with {} events", events.size());

        for (Map<String, Object> input : events) {
            try {
                String sessionId = (String) input.get("sessionId");
                Long bytesSent = input.get("bytesSent") != null
                        ? ((Number) input.get("bytesSent")).longValue()
                        : 0L;
                Long bytesReceived = input.get("bytesReceived") != null
                        ? ((Number) input.get("bytesReceived")).longValue()
                        : 0L;
                String disconnectReason = (String) input.get("disconnectReason");

                boolean success = connectionTrackingService.recordConnectionEnd(
                        sessionId, bytesSent, bytesReceived, disconnectReason);

                if (success) {
                    processedCount++;
                } else {
                    failedCount++;
                }

            } catch (Exception e) {
                log.error("Error processing batch connection end event: {}", e.getMessage());
                failedCount++;
            }
        }

        log.info("Batch connection end completed: processed={}, failed={}", processedCount, failedCount);

        result.put("success", failedCount == 0);
        result.put("message", String.format("Processed %d events, %d failed", processedCount, failedCount));
        result.put("processedCount", processedCount);
        result.put("failedCount", failedCount);

        return result;
    }
}
