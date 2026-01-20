package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.OrbMeshNode;
import com.orbvpn.api.domain.entity.OrbMeshPartner;
import com.orbvpn.api.domain.entity.OrbMeshTokenEarning;
import com.orbvpn.api.domain.enums.EarningStatus;
import com.orbvpn.api.domain.enums.EarningType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface OrbMeshTokenEarningRepository extends JpaRepository<OrbMeshTokenEarning, Long> {

    List<OrbMeshTokenEarning> findByNode(OrbMeshNode node);

    List<OrbMeshTokenEarning> findByPartner(OrbMeshPartner partner);

    List<OrbMeshTokenEarning> findByUser(User user);

    Page<OrbMeshTokenEarning> findByNode(OrbMeshNode node, Pageable pageable);

    Page<OrbMeshTokenEarning> findByPartner(OrbMeshPartner partner, Pageable pageable);

    Page<OrbMeshTokenEarning> findByUser(User user, Pageable pageable);

    List<OrbMeshTokenEarning> findByStatus(EarningStatus status);

    List<OrbMeshTokenEarning> findByEarningType(EarningType type);

    @Query("SELECT e FROM OrbMeshTokenEarning e WHERE e.partner = :partner AND e.status = 'PENDING'")
    List<OrbMeshTokenEarning> findPendingByPartner(@Param("partner") OrbMeshPartner partner);

    @Query("SELECT e FROM OrbMeshTokenEarning e WHERE e.user = :user AND e.status = 'PENDING'")
    List<OrbMeshTokenEarning> findPendingByUser(@Param("user") User user);

    @Query("SELECT SUM(e.amount) FROM OrbMeshTokenEarning e WHERE e.partner = :partner AND e.status = 'PENDING'")
    BigDecimal sumPendingByPartner(@Param("partner") OrbMeshPartner partner);

    @Query("SELECT SUM(e.amount) FROM OrbMeshTokenEarning e WHERE e.user = :user AND e.status = 'PENDING'")
    BigDecimal sumPendingByUser(@Param("user") User user);

    @Query("SELECT SUM(e.amount) FROM OrbMeshTokenEarning e WHERE e.partner = :partner AND e.status = 'PAID'")
    BigDecimal sumPaidByPartner(@Param("partner") OrbMeshPartner partner);

    @Query("SELECT SUM(e.amount) FROM OrbMeshTokenEarning e WHERE e.user = :user AND e.status = 'PAID'")
    BigDecimal sumPaidByUser(@Param("user") User user);

    @Query("SELECT e FROM OrbMeshTokenEarning e WHERE e.partner = :partner " +
           "AND e.periodStart >= :start AND e.periodEnd <= :end")
    List<OrbMeshTokenEarning> findByPartnerAndPeriod(
            @Param("partner") OrbMeshPartner partner,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT SUM(e.bandwidthGb) FROM OrbMeshTokenEarning e WHERE e.partner = :partner " +
           "AND e.periodStart >= :start AND e.periodEnd <= :end")
    BigDecimal sumBandwidthByPartnerAndPeriod(
            @Param("partner") OrbMeshPartner partner,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT SUM(e.amount) FROM OrbMeshTokenEarning e WHERE e.status = 'PENDING'")
    BigDecimal sumAllPending();

    @Query("SELECT SUM(e.amount) FROM OrbMeshTokenEarning e WHERE e.status = 'PAID'")
    BigDecimal sumAllPaid();
}
