package com.orbvpn.api.staticip;

import com.orbvpn.api.domain.entity.PortForwardRule;
import com.orbvpn.api.domain.entity.StaticIPAllocation;
import com.orbvpn.api.domain.enums.PortForwardProtocol;
import com.orbvpn.api.domain.enums.PortForwardStatus;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates network connectivity for testing Static IP and Port Forwarding
 * without requiring actual Azure infrastructure or real network connections.
 *
 * This simulator creates virtual network endpoints that behave like real
 * static IPs and port forwards, allowing comprehensive testing of the
 * entire flow.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NetworkConnectivitySimulator {

    // Virtual network state
    private final Map<String, VirtualStaticIP> virtualIPs = new ConcurrentHashMap<>();
    private final Map<String, VirtualPortForward> virtualPortForwards = new ConcurrentHashMap<>();
    private final Map<String, VirtualClient> connectedClients = new ConcurrentHashMap<>();

    // Statistics
    private final AtomicLong totalPacketsTransmitted = new AtomicLong(0);
    private final AtomicLong totalBytesTransmitted = new AtomicLong(0);
    private final AtomicLong totalConnectionsEstablished = new AtomicLong(0);

    /**
     * Provision a virtual static IP for testing
     */
    public VirtualStaticIP provisionStaticIP(StaticIPAllocation allocation) {
        String key = allocation.getPublicIp();

        VirtualStaticIP vip = new VirtualStaticIP();
        vip.setPublicIp(allocation.getPublicIp());
        vip.setInternalIp(allocation.getInternalIp());
        vip.setRegion(allocation.getRegion());
        vip.setServerId(allocation.getServerId());
        vip.setActive(true);
        vip.setNatConfigured(false);
        vip.setCreatedAt(System.currentTimeMillis());

        virtualIPs.put(key, vip);
        log.info("Provisioned virtual static IP: {} -> {}", vip.getPublicIp(), vip.getInternalIp());

        return vip;
    }

    /**
     * Configure NAT for a virtual static IP
     */
    public NatConfigResult configureNat(String publicIp, String internalIp) {
        VirtualStaticIP vip = virtualIPs.get(publicIp);
        if (vip == null) {
            return NatConfigResult.failure("Static IP not provisioned: " + publicIp);
        }

        // Simulate NAT configuration
        try {
            // Simulate some processing time
            Thread.sleep(100);

            // Verify IP formats
            if (!isValidIPv4(publicIp) || !isValidPrivateIP(internalIp)) {
                return NatConfigResult.failure("Invalid IP format");
            }

            vip.setNatConfigured(true);
            vip.setNatConfiguredAt(System.currentTimeMillis());

            log.info("NAT configured for {} -> {}", publicIp, internalIp);
            return NatConfigResult.success(publicIp, internalIp);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NatConfigResult.failure("NAT configuration interrupted");
        }
    }

    /**
     * Configure a virtual port forward
     */
    public PortForwardConfigResult configurePortForward(PortForwardRule rule) {
        StaticIPAllocation allocation = rule.getAllocation();
        String key = allocation.getPublicIp() + ":" + rule.getExternalPort();

        // Verify NAT is configured
        VirtualStaticIP vip = virtualIPs.get(allocation.getPublicIp());
        if (vip == null || !vip.isNatConfigured()) {
            return PortForwardConfigResult.failure("NAT not configured for static IP");
        }

        // Check for port conflicts
        if (virtualPortForwards.containsKey(key)) {
            return PortForwardConfigResult.failure("Port already in use: " + rule.getExternalPort());
        }

        // Create virtual port forward
        VirtualPortForward vpf = new VirtualPortForward();
        vpf.setRuleId(rule.getId());
        vpf.setExternalIp(allocation.getPublicIp());
        vpf.setExternalPort(rule.getExternalPort());
        vpf.setInternalIp(allocation.getInternalIp());
        vpf.setInternalPort(rule.getInternalPort());
        vpf.setProtocol(rule.getProtocol());
        vpf.setActive(true);
        vpf.setCreatedAt(System.currentTimeMillis());

        virtualPortForwards.put(key, vpf);
        log.info("Port forward configured: {}:{} -> {}:{}",
                vpf.getExternalIp(), vpf.getExternalPort(),
                vpf.getInternalIp(), vpf.getInternalPort());

        return PortForwardConfigResult.success(vpf);
    }

    /**
     * Remove a virtual port forward
     */
    public boolean removePortForward(String publicIp, int externalPort) {
        String key = publicIp + ":" + externalPort;
        VirtualPortForward removed = virtualPortForwards.remove(key);
        if (removed != null) {
            log.info("Port forward removed: {}:{}", publicIp, externalPort);
            return true;
        }
        return false;
    }

    /**
     * Simulate a client connecting to the VPN with a static IP
     */
    public VirtualClient connectClient(String publicIp, String clientId) {
        VirtualStaticIP vip = virtualIPs.get(publicIp);
        if (vip == null || !vip.isActive()) {
            throw new IllegalStateException("Static IP not available: " + publicIp);
        }

        VirtualClient client = new VirtualClient();
        client.setClientId(clientId);
        client.setAssignedPublicIp(publicIp);
        client.setAssignedInternalIp(vip.getInternalIp());
        client.setConnectedAt(System.currentTimeMillis());
        client.setConnected(true);

        connectedClients.put(clientId, client);
        totalConnectionsEstablished.incrementAndGet();

        log.info("Client {} connected with static IP {}", clientId, publicIp);
        return client;
    }

    /**
     * Disconnect a client
     */
    public void disconnectClient(String clientId) {
        VirtualClient client = connectedClients.remove(clientId);
        if (client != null) {
            client.setConnected(false);
            client.setDisconnectedAt(System.currentTimeMillis());
            log.info("Client {} disconnected", clientId);
        }
    }

    /**
     * Simulate sending data through a port forward from external source
     */
    public DataTransferResult sendThroughPortForward(String externalIp, int externalPort, byte[] data) {
        String key = externalIp + ":" + externalPort;
        VirtualPortForward vpf = virtualPortForwards.get(key);

        if (vpf == null || !vpf.isActive()) {
            return DataTransferResult.failure("Port forward not active");
        }

        // Simulate data transfer
        vpf.incrementTotalConnections();
        vpf.addBytesTransferred(data.length);
        totalPacketsTransmitted.incrementAndGet();
        totalBytesTransmitted.addAndGet(data.length);

        log.debug("Data transferred through port forward {}:{} -> {}:{} ({} bytes)",
                vpf.getExternalIp(), vpf.getExternalPort(),
                vpf.getInternalIp(), vpf.getInternalPort(), data.length);

        return DataTransferResult.success(vpf.getInternalIp(), vpf.getInternalPort(), data.length);
    }

    /**
     * Test TCP connectivity to a port forward
     */
    public ConnectivityTestResult testTcpConnectivity(String externalIp, int externalPort) {
        String key = externalIp + ":" + externalPort;
        VirtualPortForward vpf = virtualPortForwards.get(key);

        if (vpf == null) {
            return ConnectivityTestResult.failure("Port forward not found");
        }

        if (!vpf.isActive()) {
            return ConnectivityTestResult.failure("Port forward not active");
        }

        if (vpf.getProtocol() == PortForwardProtocol.UDP) {
            return ConnectivityTestResult.failure("TCP test on UDP-only port forward");
        }

        // Simulate TCP handshake
        long latency = simulateLatency(vpf);

        return ConnectivityTestResult.success(latency, "TCP connection successful");
    }

    /**
     * Test UDP connectivity to a port forward
     */
    public ConnectivityTestResult testUdpConnectivity(String externalIp, int externalPort) {
        String key = externalIp + ":" + externalPort;
        VirtualPortForward vpf = virtualPortForwards.get(key);

        if (vpf == null) {
            return ConnectivityTestResult.failure("Port forward not found");
        }

        if (!vpf.isActive()) {
            return ConnectivityTestResult.failure("Port forward not active");
        }

        if (vpf.getProtocol() == PortForwardProtocol.TCP) {
            return ConnectivityTestResult.failure("UDP test on TCP-only port forward");
        }

        // Simulate UDP packet
        long latency = simulateLatency(vpf);

        return ConnectivityTestResult.success(latency, "UDP packet delivered");
    }

    /**
     * Run a comprehensive connectivity test suite
     */
    public ConnectivityTestSuite runFullConnectivityTest(StaticIPAllocation allocation, PortForwardRule rule) {
        ConnectivityTestSuite suite = new ConnectivityTestSuite();
        suite.setPublicIp(allocation.getPublicIp());
        suite.setInternalIp(allocation.getInternalIp());
        suite.setExternalPort(rule.getExternalPort());
        suite.setInternalPort(rule.getInternalPort());
        suite.setStartTime(System.currentTimeMillis());

        // Test 1: Verify static IP provisioning
        VirtualStaticIP vip = virtualIPs.get(allocation.getPublicIp());
        suite.setStaticIpProvisioned(vip != null && vip.isActive());

        // Test 2: Verify NAT configuration
        suite.setNatConfigured(vip != null && vip.isNatConfigured());

        // Test 3: Verify port forward configuration
        String pfKey = allocation.getPublicIp() + ":" + rule.getExternalPort();
        VirtualPortForward vpf = virtualPortForwards.get(pfKey);
        suite.setPortForwardConfigured(vpf != null && vpf.isActive());

        // Test 4: Test TCP connectivity (if applicable)
        if (rule.getProtocol() == PortForwardProtocol.TCP ||
                rule.getProtocol() == PortForwardProtocol.BOTH) {
            ConnectivityTestResult tcpResult = testTcpConnectivity(
                    allocation.getPublicIp(), rule.getExternalPort());
            suite.setTcpConnectivitySuccess(tcpResult.isSuccess());
            suite.setTcpLatencyMs(tcpResult.getLatencyMs());
        }

        // Test 5: Test UDP connectivity (if applicable)
        if (rule.getProtocol() == PortForwardProtocol.UDP ||
                rule.getProtocol() == PortForwardProtocol.BOTH) {
            ConnectivityTestResult udpResult = testUdpConnectivity(
                    allocation.getPublicIp(), rule.getExternalPort());
            suite.setUdpConnectivitySuccess(udpResult.isSuccess());
            suite.setUdpLatencyMs(udpResult.getLatencyMs());
        }

        // Test 6: Test data transfer
        byte[] testData = "Hello, Static IP!".getBytes();
        DataTransferResult transferResult = sendThroughPortForward(
                allocation.getPublicIp(), rule.getExternalPort(), testData);
        suite.setDataTransferSuccess(transferResult.isSuccess());
        suite.setBytesTransferred(transferResult.getBytesTransferred());

        suite.setEndTime(System.currentTimeMillis());
        suite.setAllTestsPassed(
                suite.isStaticIpProvisioned() &&
                        suite.isNatConfigured() &&
                        suite.isPortForwardConfigured() &&
                        suite.isDataTransferSuccess()
        );

        log.info("Connectivity test suite completed: {}", suite.isAllTestsPassed() ? "PASSED" : "FAILED");
        return suite;
    }

    /**
     * Get simulator statistics
     */
    public SimulatorStats getStats() {
        SimulatorStats stats = new SimulatorStats();
        stats.setTotalStaticIPs(virtualIPs.size());
        stats.setActiveStaticIPs((int) virtualIPs.values().stream().filter(VirtualStaticIP::isActive).count());
        stats.setTotalPortForwards(virtualPortForwards.size());
        stats.setActivePortForwards((int) virtualPortForwards.values().stream().filter(VirtualPortForward::isActive).count());
        stats.setConnectedClients(connectedClients.size());
        stats.setTotalPacketsTransmitted(totalPacketsTransmitted.get());
        stats.setTotalBytesTransmitted(totalBytesTransmitted.get());
        stats.setTotalConnectionsEstablished(totalConnectionsEstablished.get());
        return stats;
    }

    /**
     * Reset all virtual network state
     */
    public void reset() {
        virtualIPs.clear();
        virtualPortForwards.clear();
        connectedClients.clear();
        totalPacketsTransmitted.set(0);
        totalBytesTransmitted.set(0);
        totalConnectionsEstablished.set(0);
        log.info("Network simulator reset");
    }

    // ========================================
    // Helper Methods
    // ========================================

    private boolean isValidIPv4(String ip) {
        if (ip == null) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String part : parts) {
                int val = Integer.parseInt(part);
                if (val < 0 || val > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidPrivateIP(String ip) {
        if (!isValidIPv4(ip)) return false;
        String[] parts = ip.split("\\.");
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);

        // 10.0.0.0/8
        if (first == 10) return true;
        // 172.16.0.0/12
        if (first == 172 && second >= 16 && second <= 31) return true;
        // 192.168.0.0/16
        if (first == 192 && second == 168) return true;

        return false;
    }

    private long simulateLatency(VirtualPortForward vpf) {
        // Simulate network latency (10-50ms)
        return 10 + (long) (Math.random() * 40);
    }

    // ========================================
    // Inner Classes
    // ========================================

    @Data
    public static class VirtualStaticIP {
        private String publicIp;
        private String internalIp;
        private String region;
        private Long serverId;
        private boolean active;
        private boolean natConfigured;
        private long createdAt;
        private long natConfiguredAt;
    }

    @Data
    public static class VirtualPortForward {
        private Long ruleId;
        private String externalIp;
        private int externalPort;
        private String internalIp;
        private int internalPort;
        private PortForwardProtocol protocol;
        private boolean active;
        private long createdAt;
        private long totalConnections;
        private long totalBytesTransferred;

        public void incrementTotalConnections() {
            totalConnections++;
        }

        public void addBytesTransferred(long bytes) {
            totalBytesTransferred += bytes;
        }
    }

    @Data
    public static class VirtualClient {
        private String clientId;
        private String assignedPublicIp;
        private String assignedInternalIp;
        private long connectedAt;
        private long disconnectedAt;
        private boolean connected;
        private long bytesSent;
        private long bytesReceived;
    }

    @Data
    public static class NatConfigResult {
        private boolean success;
        private String publicIp;
        private String internalIp;
        private String errorMessage;

        public static NatConfigResult success(String publicIp, String internalIp) {
            NatConfigResult result = new NatConfigResult();
            result.success = true;
            result.publicIp = publicIp;
            result.internalIp = internalIp;
            return result;
        }

        public static NatConfigResult failure(String error) {
            NatConfigResult result = new NatConfigResult();
            result.success = false;
            result.errorMessage = error;
            return result;
        }
    }

    @Data
    public static class PortForwardConfigResult {
        private boolean success;
        private VirtualPortForward portForward;
        private String errorMessage;

        public static PortForwardConfigResult success(VirtualPortForward pf) {
            PortForwardConfigResult result = new PortForwardConfigResult();
            result.success = true;
            result.portForward = pf;
            return result;
        }

        public static PortForwardConfigResult failure(String error) {
            PortForwardConfigResult result = new PortForwardConfigResult();
            result.success = false;
            result.errorMessage = error;
            return result;
        }
    }

    @Data
    public static class DataTransferResult {
        private boolean success;
        private String destinationIp;
        private int destinationPort;
        private int bytesTransferred;
        private String errorMessage;

        public static DataTransferResult success(String ip, int port, int bytes) {
            DataTransferResult result = new DataTransferResult();
            result.success = true;
            result.destinationIp = ip;
            result.destinationPort = port;
            result.bytesTransferred = bytes;
            return result;
        }

        public static DataTransferResult failure(String error) {
            DataTransferResult result = new DataTransferResult();
            result.success = false;
            result.errorMessage = error;
            return result;
        }
    }

    @Data
    public static class ConnectivityTestResult {
        private boolean success;
        private long latencyMs;
        private String message;

        public static ConnectivityTestResult success(long latency, String message) {
            ConnectivityTestResult result = new ConnectivityTestResult();
            result.success = true;
            result.latencyMs = latency;
            result.message = message;
            return result;
        }

        public static ConnectivityTestResult failure(String message) {
            ConnectivityTestResult result = new ConnectivityTestResult();
            result.success = false;
            result.message = message;
            return result;
        }
    }

    @Data
    public static class ConnectivityTestSuite {
        private String publicIp;
        private String internalIp;
        private int externalPort;
        private int internalPort;
        private long startTime;
        private long endTime;
        private boolean staticIpProvisioned;
        private boolean natConfigured;
        private boolean portForwardConfigured;
        private boolean tcpConnectivitySuccess;
        private long tcpLatencyMs;
        private boolean udpConnectivitySuccess;
        private long udpLatencyMs;
        private boolean dataTransferSuccess;
        private int bytesTransferred;
        private boolean allTestsPassed;

        public long getDurationMs() {
            return endTime - startTime;
        }
    }

    @Data
    public static class SimulatorStats {
        private int totalStaticIPs;
        private int activeStaticIPs;
        private int totalPortForwards;
        private int activePortForwards;
        private int connectedClients;
        private long totalPacketsTransmitted;
        private long totalBytesTransmitted;
        private long totalConnectionsEstablished;
    }
}
