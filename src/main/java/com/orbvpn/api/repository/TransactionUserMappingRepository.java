package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.TransactionUserMapping;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionUserMappingRepository extends JpaRepository<TransactionUserMapping, Integer> {
    Optional<TransactionUserMapping> findByTransactionIdAndGateway(String transactionId, GatewayName gateway);

    // Existing method to find mappings by email, gateway, and where user is null
    List<TransactionUserMapping> findByEmailAndGatewayAndUserIsNull(String email, GatewayName gateway);

    // New method to find mappings by user
    List<TransactionUserMapping> findByUser(User user);

    // **Modified method to return Optional**
    Optional<TransactionUserMapping> findFirstByEmailAndGateway(String email, GatewayName gateway);

    long countByEmail(String email);

}