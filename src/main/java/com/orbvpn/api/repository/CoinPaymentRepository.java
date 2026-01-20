package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.CoinPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface CoinPaymentRepository extends JpaRepository<CoinPayment, Long> {
    List<CoinPayment> findByStatusAndCreatedAtBefore(Boolean status, LocalDateTime date);
}