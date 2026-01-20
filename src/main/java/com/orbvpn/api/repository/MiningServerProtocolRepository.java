package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.MiningServerProtocol;
import com.orbvpn.api.domain.enums.ProtocolType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MiningServerProtocolRepository extends JpaRepository<MiningServerProtocol, Long> {
    List<MiningServerProtocol> findByMiningServer_IdAndEnabled(Long miningServerId, Boolean enabled);

    List<MiningServerProtocol> findByTypeAndEnabled(ProtocolType type, Boolean enabled);
}