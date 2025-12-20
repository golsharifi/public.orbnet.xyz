package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.MacOuiEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MacOuiEntryRepository extends JpaRepository<MacOuiEntry, Long> {

    Optional<MacOuiEntry> findByOuiPrefix(String ouiPrefix);

    List<MacOuiEntry> findByVendorNameContainingIgnoreCase(String vendorName);

    List<MacOuiEntry> findByCommonDeviceType(String deviceType);

    @Query("SELECT m FROM MacOuiEntry m WHERE UPPER(m.ouiPrefix) = UPPER(:prefix)")
    Optional<MacOuiEntry> findByOuiPrefixIgnoreCase(@Param("prefix") String prefix);

    @Query("SELECT m FROM MacOuiEntry m ORDER BY m.seenCount DESC")
    List<MacOuiEntry> findMostCommonOuis(org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("UPDATE MacOuiEntry m SET m.seenCount = m.seenCount + 1 WHERE m.ouiPrefix = :prefix")
    void incrementSeenCount(@Param("prefix") String prefix);

    boolean existsByOuiPrefix(String ouiPrefix);

    long countBySource(String source);
}
