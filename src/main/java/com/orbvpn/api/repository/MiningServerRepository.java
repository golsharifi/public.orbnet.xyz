package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.MiningServer;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.ProtocolType;
import com.orbvpn.api.repository.projections.ContinentStatsProjection;
import com.orbvpn.api.repository.projections.ProtocolStatsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MiningServerRepository extends JpaRepository<MiningServer, Long> {
    List<MiningServer> findByMiningEnabledTrue();

    MiningServer findByHostName(String hostName);

    @Query("SELECT s FROM MiningServer s WHERE s.miningEnabled = true ORDER BY s.location")
    List<MiningServer> findAllByLocationOrder();

    @Query("SELECT s FROM MiningServer s WHERE s.miningEnabled = true ORDER BY s.continent, s.country, s.city")
    List<MiningServer> findAllByContinentalOrder();

    @Query("SELECT s FROM MiningServer s WHERE s.miningEnabled = true AND s.cryptoFriendly = true ORDER BY s.location")
    List<MiningServer> findAllCryptoFriendly();

    @Query("SELECT s FROM MiningServer s WHERE s.miningEnabled = true ORDER BY s.activeConnections")
    List<MiningServer> findAllByConnectionsOrder();

    @Query("SELECT DISTINCT s FROM MiningServer s JOIN s.protocols p WHERE p.type = :protocol AND s.miningEnabled = true")
    List<MiningServer> findByProtocolType(@Param("protocol") ProtocolType protocol);

    @Query("SELECT DISTINCT s FROM MiningServer s JOIN s.protocols p " +
            "WHERE p.type = :protocol AND s.miningEnabled = true " +
            "ORDER BY s.location ASC, s.id ASC")
    List<MiningServer> findByProtocolTypeAndSortByLocationAsc(@Param("protocol") ProtocolType protocol);

    @Query("SELECT DISTINCT s FROM MiningServer s JOIN s.protocols p " +
            "WHERE p.type = :protocol AND s.miningEnabled = true " +
            "ORDER BY s.location DESC, s.id ASC")
    List<MiningServer> findByProtocolTypeAndSortByLocationDesc(@Param("protocol") ProtocolType protocol);

    @Query("SELECT DISTINCT s FROM MiningServer s JOIN s.protocols p " +
            "WHERE p.type = :protocol AND s.miningEnabled = true " +
            "ORDER BY s.continent ASC, s.country ASC, s.city ASC")
    List<MiningServer> findByProtocolTypeAndSortByContinentalAsc(@Param("protocol") ProtocolType protocol);

    @Query("SELECT DISTINCT s FROM MiningServer s JOIN s.protocols p " +
            "WHERE p.type = :protocol AND s.miningEnabled = true " +
            "ORDER BY s.continent DESC, s.country DESC, s.city DESC")
    List<MiningServer> findByProtocolTypeAndSortByContinentalDesc(@Param("protocol") ProtocolType protocol);

    @Query("SELECT DISTINCT s FROM MiningServer s JOIN s.protocols p " +
            "WHERE p.type = :protocol AND s.miningEnabled = true AND s.cryptoFriendly = true " +
            "ORDER BY s.country ASC, s.city ASC")
    List<MiningServer> findByProtocolTypeAndSortByCryptoFriendlyAsc(@Param("protocol") ProtocolType protocol);

    @Query("SELECT DISTINCT s FROM MiningServer s JOIN s.protocols p " +
            "WHERE p.type = :protocol AND s.miningEnabled = true AND s.cryptoFriendly = true " +
            "ORDER BY s.country DESC, s.city DESC")
    List<MiningServer> findByProtocolTypeAndSortByCryptoFriendlyDesc(@Param("protocol") ProtocolType protocol);

    @Query("SELECT DISTINCT s FROM MiningServer s JOIN s.protocols p " +
            "WHERE p.type = :protocol AND s.miningEnabled = true " +
            "ORDER BY s.activeConnections ASC")
    List<MiningServer> findByProtocolTypeAndSortByConnectionsAsc(@Param("protocol") ProtocolType protocol);

    @Query("SELECT DISTINCT s FROM MiningServer s JOIN s.protocols p " +
            "WHERE p.type = :protocol AND s.miningEnabled = true " +
            "ORDER BY s.activeConnections DESC")
    List<MiningServer> findByProtocolTypeAndSortByConnectionsDesc(@Param("protocol") ProtocolType protocol);

    @Query("SELECT COUNT(s) FROM MiningServer s WHERE s.miningEnabled = true")
    int countActiveServers();

    @Query("SELECT " +
            "p.type as protocol, " +
            "COUNT(DISTINCT s) as serverCount, " +
            "COUNT(DISTINCT CASE WHEN s.miningEnabled = true THEN s END) as activeCount " +
            "FROM MiningServer s " +
            "JOIN s.protocols p " +
            "GROUP BY p.type")
    List<ProtocolStatsProjection> getProtocolStats();

    @Query("SELECT " +
            "s.continent as continent, " +
            "COUNT(DISTINCT s) as serverCount, " +
            "GROUP_CONCAT(DISTINCT s.country) as countries " +
            "FROM MiningServer s " +
            "GROUP BY s.continent")
    List<ContinentStatsProjection> getContinentStats();

    @Query("SELECT COUNT(s) FROM MiningServer s WHERE s.cryptoFriendly = true")
    int countCryptoFriendlyServers();

    List<MiningServer> findByOperator(User operator);

    @Query("SELECT m FROM MiningServer m LEFT JOIN FETCH m.operator WHERE m.id = :id")
    Optional<MiningServer> findByIdWithOperator(@Param("id") Long id);
}
