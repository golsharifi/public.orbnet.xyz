package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.Gateway;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayRepository extends JpaRepository<Gateway, Integer> {

}
