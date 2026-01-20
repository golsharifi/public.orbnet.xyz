package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.service.user.UserContextService;
import com.orbvpn.api.domain.enums.ProtocolType;
import com.orbvpn.api.domain.enums.SortType;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MiningServerService {
    private final MiningServerRepository miningServerRepository;
    private final MiningServerProtocolRepository protocolRepository;
    private final ServerMetricsRepository serverMetricsRepository;
    private final UserUuidService userUuidService;
    private final UserContextService userContextService;
    private final MiningSettingsRepository miningSettingsRepository;

    @Transactional
    public MiningSettingsView updateMiningSettings(MiningSettingsInput input, User user) {
        MiningSettingsEntity settings = miningSettingsRepository.findByUser(user)
                .orElse(MiningSettingsEntity.builder()
                        .user(user)
                        .build());

        settings.setWithdrawAddress(input.getWithdrawAddress());
        settings.setMinWithdrawAmount(input.getMinWithdrawAmount());
        settings.setAutoWithdraw(input.getAutoWithdraw());
        settings.setNotificationsEnabled(input.getNotifications());

        settings = miningSettingsRepository.save(settings);

        return convertToView(settings);
    }

    private MiningSettingsView convertToView(MiningSettingsEntity settings) {
        return MiningSettingsView.builder()
                .withdrawAddress(settings.getWithdrawAddress())
                .minWithdrawAmount(settings.getMinWithdrawAmount())
                .autoWithdraw(settings.getAutoWithdraw())
                .notifications(settings.getNotificationsEnabled())
                .build();
    }

    @Transactional(readOnly = true)
    public List<MiningServerView> getMiningServers(User currentUser) {
        String userUuid = userUuidService.getOrCreateUuid(currentUser.getId());
        return miningServerRepository.findByMiningEnabledTrue().stream()
                .map(server -> mapToServerView(server, userUuid))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MiningServerView> getServersByProtocol(ProtocolType protocol, User currentUser) {
        String userUuid = userUuidService.getOrCreateUuid(currentUser.getId());
        return protocolRepository.findByTypeAndEnabled(protocol, true).stream()
                .map(p -> p.getMiningServer())
                .distinct()
                .map(server -> mapToServerView(server, userUuid))
                .collect(Collectors.toList());
    }

    private List<MiningServerProtocolView> mapProtocols(MiningServer server, String userUuid) {
        return protocolRepository.findByMiningServer_IdAndEnabled(server.getId(), true).stream()
                .map(protocol -> buildProtocolView(protocol, userUuid))
                .collect(Collectors.toList());
    }

    private MiningServerProtocolView buildProtocolView(MiningServerProtocol protocol, String userUuid) {
        return MiningServerProtocolView.builder()
                .id(protocol.getId())
                .type(protocol.getType())
                .port(protocol.getPort())
                .enabled(protocol.getEnabled())
                .configString(generateConfigString(protocol, userUuid))
                .publicKey(protocol.getPublicKey())
                .build();
    }

    private String generateConfigString(MiningServerProtocol protocol, String userUuid) {
        switch (protocol.getType()) {
            case VLESS:
                return String.format("vless://%s@%s:%d?security=tls&encryption=none&type=ws",
                        userUuid, protocol.getMiningServer().getHostName(), protocol.getPort());
            case REALITY:
                return String.format("vless://%s@%s:%d?security=reality&encryption=none&pbk=%s",
                        userUuid, protocol.getMiningServer().getHostName(), protocol.getPort(),
                        protocol.getPublicKey());
            // Add other protocols as needed
            default:
                return "";
        }
    }

    private com.orbvpn.api.domain.dto.ServerMetrics mapMetrics(MiningServer server) {
        com.orbvpn.api.domain.entity.ServerMetrics latestMetrics = serverMetricsRepository
                .findFirstByServerOrderByLastCheckDesc(server);

        if (latestMetrics != null) {
            return convertEntityMetricsToDto(latestMetrics);
        }

        return com.orbvpn.api.domain.dto.ServerMetrics.builder()
                .cpuUsage(server.getCpuUsage())
                .memoryUsage(server.getMemoryUsage())
                .networkSpeed(server.getNetworkSpeed())
                .activeConnections(server.getActiveConnections())
                .lastCheck(server.getLastHeartbeat())
                .build();
    }

    private com.orbvpn.api.domain.dto.ServerMetrics convertEntityMetricsToDto(
            com.orbvpn.api.domain.entity.ServerMetrics entityMetrics) {
        if (entityMetrics == null) {
            return null;
        }

        return com.orbvpn.api.domain.dto.ServerMetrics.builder()
                .cpuUsage(entityMetrics.getCpuUsage())
                .memoryUsage(entityMetrics.getMemoryUsage())
                .uploadSpeed(entityMetrics.getUploadSpeed())
                .downloadSpeed(entityMetrics.getDownloadSpeed())
                .networkSpeed(entityMetrics.getNetworkSpeed())
                .activeConnections(entityMetrics.getActiveConnections())
                .maxConnections(entityMetrics.getMaxConnections())
                .uptime(entityMetrics.getUptime())
                .responseTime(entityMetrics.getResponseTime())
                .lastCheck(entityMetrics.getLastCheck())
                .build();
    }

    @Transactional
    public MiningServerView enableMiningServer(Long serverId) {
        MiningServer server = miningServerRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found"));

        server.setMiningEnabled(true);
        server = miningServerRepository.save(server);

        return mapToServerView(server,
                userUuidService.getOrCreateUuid(userContextService.getCurrentUser().getId()));
    }

    @Transactional
    public MiningServerView disableMiningServer(Long serverId) {
        MiningServer server = miningServerRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found"));

        server.setMiningEnabled(false);
        server = miningServerRepository.save(server);

        return mapToServerView(server,
                userUuidService.getOrCreateUuid(userContextService.getCurrentUser().getId()));
    }

    public ServersByProtocol getAllServersByProtocol(
            SortType sortBy,
            Boolean ascending,
            User currentUser) {
        String userUuid = userUuidService.getOrCreateUuid(currentUser.getId());

        return ServersByProtocol.builder()
                .vlessServers(getServersForProtocol(ProtocolType.VLESS, sortBy, ascending, userUuid))
                .realityServers(getServersForProtocol(ProtocolType.REALITY, sortBy, ascending, userUuid))
                .wireguardServers(getServersForProtocol(ProtocolType.WIREGUARD, sortBy, ascending, userUuid))
                .openconnectServers(getServersForProtocol(ProtocolType.OPENCONNECT, sortBy, ascending, userUuid))
                .build();
    }

    private List<MiningServerView> getServersForProtocol(
            ProtocolType protocol,
            SortType sortBy,
            Boolean ascending,
            String userUuid) {
        List<MiningServer> servers;

        if (sortBy == null) {
            servers = miningServerRepository.findByProtocolType(protocol);
        } else {
            switch (sortBy) {
                case LOCATION:
                    servers = ascending ? miningServerRepository.findByProtocolTypeAndSortByLocationAsc(protocol)
                            : miningServerRepository.findByProtocolTypeAndSortByLocationDesc(protocol);
                    break;
                case CONTINENTAL:
                    servers = ascending ? miningServerRepository.findByProtocolTypeAndSortByContinentalAsc(protocol)
                            : miningServerRepository.findByProtocolTypeAndSortByContinentalDesc(protocol);
                    break;
                case CRYPTO_FRIENDLY:
                    servers = ascending ? miningServerRepository.findByProtocolTypeAndSortByCryptoFriendlyAsc(protocol)
                            : miningServerRepository.findByProtocolTypeAndSortByCryptoFriendlyDesc(protocol);
                    break;
                case CONNECTIONS:
                    servers = ascending ? miningServerRepository.findByProtocolTypeAndSortByConnectionsAsc(protocol)
                            : miningServerRepository.findByProtocolTypeAndSortByConnectionsDesc(protocol);
                    break;
                default:
                    servers = miningServerRepository.findByProtocolType(protocol);
            }
        }

        return servers.stream()
                .map(server -> mapToServerView(server, userUuid))
                .collect(Collectors.toList());
    }

    private MiningServerView mapToServerView(MiningServer server, String userUuid) {
        return MiningServerView.builder()
                .id(server.getId())
                .operatorId(server.getOperator() != null ? server.getOperator().getId() : null)
                .operatorEmail(server.getOperator() != null ? server.getOperator().getEmail() : null)
                .hostName(server.getHostName())
                .publicIp(server.getPublicIp())
                .location(String.format("%s, %s", server.getCity(), server.getCountry()))
                .city(server.getCity())
                .country(server.getCountry())
                .continent(server.getContinent())
                .cryptoFriendly(server.getCryptoFriendly())
                .activeConnections(server.getActiveConnections())
                .protocols(mapProtocols(server, userUuid))
                .metrics(mapMetrics(server))
                .build();
    }

    public ServerStats getServerStats() {
        return ServerStats.builder()
                .totalServers((int) miningServerRepository.count())
                .totalActiveServers(miningServerRepository.countActiveServers())
                .serversByProtocol(getProtocolStats())
                .serversByContinent(getContinentStats())
                .cryptoFriendlyCount(miningServerRepository.countCryptoFriendlyServers())
                .build();
    }

    private List<ProtocolStats> getProtocolStats() {
        return miningServerRepository.getProtocolStats().stream()
                .map(projection -> ProtocolStats.builder()
                        .protocol(projection.getProtocol())
                        .count(projection.getServerCount().intValue())
                        .activeCount(projection.getActiveCount().intValue())
                        .build())
                .collect(Collectors.toList());
    }

    private List<ContinentStats> getContinentStats() {
        return miningServerRepository.getContinentStats().stream()
                .map(projection -> ContinentStats.builder()
                        .continent(projection.getContinent())
                        .count(projection.getServerCount().intValue())
                        .countries(Arrays.asList(projection.getCountries().split(",")))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MiningServer getServerById(Long serverId) {
        return miningServerRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Mining server not found with ID: " + serverId));
    }

    @Transactional
    public MiningServer assignOperator(Long serverId, User operator) {
        MiningServer server = getServerById(serverId);
        server.setOperator(operator);
        return miningServerRepository.save(server);
    }

    @Transactional(readOnly = true)
    public User getServerOperator(Long serverId) {
        MiningServer server = getServerById(serverId);
        return server.getOperator();
    }
}
