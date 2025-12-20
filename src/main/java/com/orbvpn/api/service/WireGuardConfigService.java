package com.orbvpn.api.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.orbvpn.api.domain.entity.OrbMeshServer;
import com.orbvpn.api.domain.entity.OrbMeshWireGuardConfig;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.exception.BadRequestException;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.OrbMeshWireGuardConfigRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class WireGuardConfigService {

    private final OrbMeshWireGuardConfigRepository configRepository;
    private final GlobalSettingsService globalSettingsService;

    private static final String DEFAULT_DNS = "1.1.1.1, 1.0.0.1";
    private static final int DEFAULT_MTU = 1420;
    private static final int DEFAULT_PERSISTENT_KEEPALIVE = 25;
    private static final int QR_CODE_SIZE = 300;

    /**
     * Generate standard WireGuard .conf file content
     */
    public String generateConfigFile(OrbMeshWireGuardConfig config) {
        OrbMeshServer server = config.getServer();
        String endpoint = getServerEndpoint(server);

        StringBuilder sb = new StringBuilder();

        // [Interface] section
        sb.append("[Interface]\n");
        sb.append("PrivateKey = ").append(config.getPrivateKey()).append("\n");
        sb.append("Address = ").append(config.getAllocatedIp()).append("/32\n");
        sb.append("DNS = ").append(DEFAULT_DNS).append("\n");
        sb.append("MTU = ").append(DEFAULT_MTU).append("\n");
        sb.append("\n");

        // [Peer] section
        sb.append("[Peer]\n");
        sb.append("PublicKey = ").append(server.getWireguardPublicKey()).append("\n");
        sb.append("AllowedIPs = 0.0.0.0/0, ::/0\n");
        sb.append("Endpoint = ").append(endpoint).append(":").append(getWireguardPort(server)).append("\n");
        sb.append("PersistentKeepalive = ").append(DEFAULT_PERSISTENT_KEEPALIVE).append("\n");

        return sb.toString();
    }

    /**
     * Generate QR code image (base64 encoded PNG) for a WireGuard config
     */
    public String generateQrCode(OrbMeshWireGuardConfig config) {
        String configContent = generateConfigFile(config);
        return generateQrCodeFromContent(configContent);
    }

    /**
     * Generate QR code image from any content string
     */
    private String generateQrCodeFromContent(String content) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            log.error("Error generating QR code", e);
            throw new BadRequestException("Error generating QR code: " + e.getMessage());
        }
    }

    /**
     * Get WireGuard config export data for a user
     */
    @Transactional(readOnly = true)
    public WireGuardExportData getConfigExport(User user, Long configId) {
        if (!globalSettingsService.isThirdPartyWireGuardClientsAllowed()) {
            throw new BadRequestException("Third-party WireGuard clients are not enabled. Please contact your administrator.");
        }

        OrbMeshWireGuardConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new NotFoundException("WireGuard config not found"));

        if (!config.getUserUuid().equals(user.getUuid())) {
            throw new BadRequestException("You do not have access to this configuration");
        }

        if (!config.getActive()) {
            throw new BadRequestException("This configuration has been revoked");
        }

        String configFile = generateConfigFile(config);
        String qrCode = generateQrCode(config);
        boolean showPrivateKey = globalSettingsService.isShowWireGuardPrivateKeysAllowed();

        return WireGuardExportData.builder()
                .configId(configId)
                .serverName(config.getServer().getName())
                .serverLocation(config.getServer().getLocation())
                .serverCountry(config.getServer().getCountry())
                .configFile(configFile)
                .qrCodeImage(qrCode)
                .privateKey(showPrivateKey ? config.getPrivateKey() : null)
                .publicKey(config.getPublicKey())
                .allocatedIp(config.getAllocatedIp())
                .serverEndpoint(getServerEndpoint(config.getServer()))
                .serverPort(getWireguardPort(config.getServer()))
                .serverPublicKey(config.getServer().getWireguardPublicKey())
                .dns(DEFAULT_DNS)
                .mtu(DEFAULT_MTU)
                .persistentKeepalive(DEFAULT_PERSISTENT_KEEPALIVE)
                .build();
    }

    /**
     * Get all WireGuard config exports for a user
     */
    @Transactional(readOnly = true)
    public List<WireGuardExportData> getAllConfigExports(User user) {
        if (!globalSettingsService.isThirdPartyWireGuardClientsAllowed()) {
            throw new BadRequestException("Third-party WireGuard clients are not enabled. Please contact your administrator.");
        }

        List<OrbMeshWireGuardConfig> configs = configRepository.findByUserUuidAndActiveTrue(user.getUuid());

        return configs.stream()
                .map(config -> {
                    String configFile = generateConfigFile(config);
                    String qrCode = generateQrCode(config);
                    boolean showPrivateKey = globalSettingsService.isShowWireGuardPrivateKeysAllowed();

                    return WireGuardExportData.builder()
                            .configId(config.getId())
                            .serverName(config.getServer().getName())
                            .serverLocation(config.getServer().getLocation())
                            .serverCountry(config.getServer().getCountry())
                            .configFile(configFile)
                            .qrCodeImage(qrCode)
                            .privateKey(showPrivateKey ? config.getPrivateKey() : null)
                            .publicKey(config.getPublicKey())
                            .allocatedIp(config.getAllocatedIp())
                            .serverEndpoint(getServerEndpoint(config.getServer()))
                            .serverPort(getWireguardPort(config.getServer()))
                            .serverPublicKey(config.getServer().getWireguardPublicKey())
                            .dns(DEFAULT_DNS)
                            .mtu(DEFAULT_MTU)
                            .persistentKeepalive(DEFAULT_PERSISTENT_KEEPALIVE)
                            .build();
                })
                .toList();
    }

    /**
     * Admin: Get WireGuard config exports for a specific user
     */
    @Transactional(readOnly = true)
    public List<WireGuardExportData> getConfigExportsForUser(String userUuid) {
        List<OrbMeshWireGuardConfig> configs = configRepository.findByUserUuidAndActiveTrue(userUuid);

        return configs.stream()
                .map(config -> {
                    String configFile = generateConfigFile(config);
                    String qrCode = generateQrCode(config);

                    return WireGuardExportData.builder()
                            .configId(config.getId())
                            .serverName(config.getServer().getName())
                            .serverLocation(config.getServer().getLocation())
                            .serverCountry(config.getServer().getCountry())
                            .configFile(configFile)
                            .qrCodeImage(qrCode)
                            .privateKey(config.getPrivateKey())
                            .publicKey(config.getPublicKey())
                            .allocatedIp(config.getAllocatedIp())
                            .serverEndpoint(getServerEndpoint(config.getServer()))
                            .serverPort(getWireguardPort(config.getServer()))
                            .serverPublicKey(config.getServer().getWireguardPublicKey())
                            .dns(DEFAULT_DNS)
                            .mtu(DEFAULT_MTU)
                            .persistentKeepalive(DEFAULT_PERSISTENT_KEEPALIVE)
                            .build();
                })
                .toList();
    }

    /**
     * Check if third-party WireGuard clients are allowed
     */
    public boolean isThirdPartyClientsAllowed() {
        return globalSettingsService.isThirdPartyWireGuardClientsAllowed();
    }

    private String getServerEndpoint(OrbMeshServer server) {
        if (server.getHostname() != null && !server.getHostname().isEmpty()) {
            return server.getHostname();
        }
        return server.getIpAddress();
    }

    private int getWireguardPort(OrbMeshServer server) {
        return server.getWireguardPort() != null ? server.getWireguardPort() : 51820;
    }

    /**
     * Data class for WireGuard config export
     */
    @Data
    @Builder
    public static class WireGuardExportData {
        private Long configId;
        private String serverName;
        private String serverLocation;
        private String serverCountry;
        private String configFile;
        private String qrCodeImage;
        private String privateKey;
        private String publicKey;
        private String allocatedIp;
        private String serverEndpoint;
        private Integer serverPort;
        private String serverPublicKey;
        private String dns;
        private Integer mtu;
        private Integer persistentKeepalive;
    }
}
