package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.Reseller;
import com.orbvpn.api.domain.entity.User;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResellerRepository extends JpaRepository<Reseller, Integer> {
  List<Reseller> findAllByEnabled(boolean enabled);

  Optional<Reseller> findResellerByUser(User user);

  List<Reseller> findByLevelSetDateBefore(LocalDateTime levelSetDate);

  @Query("select sum(r.credit) from Reseller r where r.level.name <> com.orbvpn.api.domain.enums.ResellerLevelName.OWNER")
  BigDecimal getResellersTotalCredit();

  void deleteAllByUser(User user);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM Reseller r WHERE r.id = :id")
  Optional<Reseller> findByIdWithLock(@Param("id") Integer id);
}