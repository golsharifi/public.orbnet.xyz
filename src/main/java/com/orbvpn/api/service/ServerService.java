package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.ClientServerView;
import com.orbvpn.api.domain.dto.ServerEdit;
import com.orbvpn.api.domain.dto.ServerView;
import com.orbvpn.api.domain.entity.CongestionLevel;
import com.orbvpn.api.domain.entity.Server;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.mapper.ServerEditMapper;
import com.orbvpn.api.mapper.ServerViewMapper;
import com.orbvpn.api.repository.CongestionLevelRepository;
import com.orbvpn.api.repository.ServerRepository;
import com.orbvpn.api.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ServerService {
    private final ServerRepository serverRepository;
    private final CongestionLevelRepository congestionLevelRepository;
    private final ServerEditMapper serverEditMapper;
    private final ServerViewMapper serverViewMapper;
    private final UserService userService;
    private final ServerMetricsCache serverMetricsCache;

    private final RadiusService radiusService;

    @CacheEvict(value = CacheConfig.SERVERS_CACHE, allEntries = true)
    public ServerView createServer(ServerEdit serverEdit) {
        log.info("Creating server with data {}", serverEdit);
        Server server = serverEditMapper.create(serverEdit);

        serverRepository.save(server);
        radiusService.createNas(server);
        ServerView serverView = serverViewMapper.toView(server);
        log.info("Created server {}", serverView);
        return serverView;
    }

    @CacheEvict(value = CacheConfig.SERVERS_CACHE, allEntries = true)
    public ServerView editServer(int id, ServerEdit serverEdit) {
        log.info("Editing server with id {} with data {}", id, serverEdit);

        Server server = getServerById(id);
        String publicIp = server.getPublicIp();
        server = serverEditMapper.edit(server, serverEdit);
        serverRepository.save(server);
        radiusService.editNas(publicIp, server);

        ServerView serverView = serverViewMapper.toView(server);
        log.info("Edited server {}", serverView);
        return serverView;
    }

    @CacheEvict(value = CacheConfig.SERVERS_CACHE, allEntries = true)
    public ServerView deleteServer(int id) {
        log.info("Deleting server with id {}", id);

        Server server = getServerById(id);
        serverRepository.delete(server);
        radiusService.deleteNas(server);

        ServerView serverView = serverViewMapper.toView(server);
        log.info("Deleted server {}", serverView);
        return serverView;
    }

    public ServerView getServer(int id) {
        Server server = getServerById(id);

        return serverViewMapper.toView(server);
    }

    @Cacheable(value = CacheConfig.SERVERS_CACHE, key = "'allServers'")
    public List<ServerView> getServers() {
        return serverRepository.findAll()
                .stream()
                .map(serverViewMapper::toView)
                .collect(Collectors.toList());
    }

    @Cacheable(value = CacheConfig.SERVERS_CACHE, key = "'visibleServers'")
    public List<ClientServerView> getClientServers() {
        return serverRepository.findAllVisible()
                .stream()
                .map(serverViewMapper::toClientView)
                .collect(Collectors.toList());
    }

    public List<ClientServerView> getClientSortedServers(String sortBy, String parameter) {
        User user = userService.getUser();
        String sortProperties;

        switch (sortBy) {
            case "recent-connection":
                String email = user.getEmail();
                return serverRepository.findServerByRecentConnection(email)
                        .stream()
                        .map(serverViewMapper::toClientView).collect(Collectors.toList());
            case "congestion":
                List<ClientServerView> ClientServerViewList = getClientServers();

                List<CongestionLevel> CongestionLevelList = new ArrayList<>(congestionLevelRepository.findAll());

                // Use cached metrics instead of SSH calls - instant response
                int totalUserCount = serverMetricsCache.getTotalConnectedUsers();
                if (totalUserCount == 0) {
                    totalUserCount = 1; // Avoid division by zero
                }

                for (ClientServerView clientServerView : ClientServerViewList) {
                    int connectedUserCount = serverMetricsCache.getConnectedUserCount(clientServerView.getId());
                    clientServerView.setConnectedUserCount(connectedUserCount);

                    var percent = (connectedUserCount * 100) / totalUserCount;

                    for (CongestionLevel congestionLevel : CongestionLevelList) {
                        if ((percent >= congestionLevel.getMin()) && (percent <= congestionLevel.getMax())) {
                            clientServerView.setCongestionLevel(congestionLevel.getName());
                        }
                    }
                }

                ClientServerViewList.sort((o1, o2) -> {
                    if (o1.getConnectedUserCount() == o2.getConnectedUserCount()) {
                        return 0;
                    } else if (o1.getConnectedUserCount() < o2.getConnectedUserCount()) {
                        return 1;
                    }
                    return -1;
                });
                return ClientServerViewList;
            case "alphabetic":
                return serverRepository.findAllVisibleOrderByHostName()
                        .stream()
                        .map(serverViewMapper::toClientView)
                        .collect(Collectors.toList());
            case "continental":
                return serverRepository.findAllVisibleOrderByContinent()
                        .stream()
                        .map(serverViewMapper::toClientView)
                        .collect(Collectors.toList());
            case "crypto-friendly":
                return serverRepository.findAllVisibleCryptoFriendly()
                        .stream()
                        .map(serverViewMapper::toClientView)
                        .collect(Collectors.toList());
            case "hero":
                return serverRepository.findAllVisibleHero()
                        .stream()
                        .map(serverViewMapper::toClientView)
                        .collect(Collectors.toList());
            case "spot":
                return serverRepository.findAllVisibleSpot()
                        .stream()
                        .map(serverViewMapper::toClientView)
                        .collect(Collectors.toList());
            case "zeus":
                return serverRepository.findAllVisibleZeus()
                        .stream()
                        .map(serverViewMapper::toClientView)
                        .collect(Collectors.toList());
            case "orb":
                return serverRepository.findAllVisibleOrb()
                        .stream()
                        .map(serverViewMapper::toClientView)
                        .collect(Collectors.toList());
            default:
                ClientServerViewList = getClientServers();
                return ClientServerViewList;
        }
    }

    public Server getServerById(int id) {
        return serverRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(Server.class, id));
    }
}
