package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.UsedTransactionId;
import com.orbvpn.api.domain.enums.GatewayName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsedTransactionIdRepository extends JpaRepository<UsedTransactionId, Long> {
    Optional<UsedTransactionId> findByTransactionIdAndGateway(String transactionId, GatewayName gateway);
}
