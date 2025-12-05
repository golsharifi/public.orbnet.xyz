package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.StripeCustomer;
import com.orbvpn.api.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface StripeCustomerRepository extends JpaRepository<StripeCustomer, Long> {
  Optional<StripeCustomer> findByUser(User user);
}