package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.Server;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ServerRepository extends JpaRepository<Server, Integer> {
    Server findByPrivateIp(String privateIp);
    Server findByCryptoFriendly(int cryptoFriendly);

    @Query(value = "SELECT DISTINCT ON (server.id) server.* FROM radacct JOIN server ON radacct.nasipaddress = server.private_ip WHERE radacct.username=?1 AND hide=0 ORDER BY server.id, radacct.acctupdatetime DESC", nativeQuery=true)
    Collection<Server> findServerByRecentConnection(String Email);

    // Bridge server queries
    List<Server> findByBridgeCapableTrueAndHideOrderByBridgePriorityAsc(int hide);

    default List<Server> findBridgeServers() {
        return findByBridgeCapableTrueAndHideOrderByBridgePriorityAsc(0);
    }

    @Query("SELECT s FROM Server s WHERE s.bridgeCapable = true AND s.hide = 0 AND s.country = :countryCode ORDER BY s.bridgePriority ASC")
    List<Server> findBridgeServersByCountry(@Param("countryCode") String countryCode);

    @Query("SELECT s FROM Server s WHERE s.bridgeCapable = true AND s.hide = 0 ORDER BY s.bridgePriority ASC")
    List<Server> findAllBridgeServers();

    // Optimized queries to filter at DB level instead of in-memory

    @Query("SELECT s FROM Server s WHERE s.hide = 0")
    List<Server> findAllVisible();

    @Query("SELECT s FROM Server s WHERE s.hide = 0 ORDER BY s.hostName ASC")
    List<Server> findAllVisibleOrderByHostName();

    @Query("SELECT s FROM Server s WHERE s.hide = 0 ORDER BY s.continent ASC")
    List<Server> findAllVisibleOrderByContinent();

    @Query("SELECT s FROM Server s WHERE s.hide = 0 AND s.cryptoFriendly = 1 ORDER BY s.hostName ASC")
    List<Server> findAllVisibleCryptoFriendly();

    @Query("SELECT s FROM Server s WHERE s.hide = 0 AND s.hero IS NOT NULL AND s.hero <> '' ORDER BY s.hero ASC")
    List<Server> findAllVisibleHero();

    @Query("SELECT s FROM Server s WHERE s.hide = 0 AND s.spot IS NOT NULL AND s.spot <> '' ORDER BY s.spot ASC")
    List<Server> findAllVisibleSpot();

    @Query("SELECT s FROM Server s WHERE s.hide = 0 AND s.zeus IS NOT NULL AND s.zeus <> '' ORDER BY s.zeus ASC")
    List<Server> findAllVisibleZeus();

    @Query("SELECT s FROM Server s WHERE s.hide = 0 AND (s.zeus IS NULL OR s.zeus = '') AND (s.spot IS NULL OR s.spot = '') AND (s.hero IS NULL OR s.hero = '') ORDER BY s.hostName ASC")
    List<Server> findAllVisibleOrb();
}
