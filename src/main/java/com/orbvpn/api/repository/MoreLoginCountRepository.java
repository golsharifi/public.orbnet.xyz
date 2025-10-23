package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.MoreLoginCount;
import com.orbvpn.api.domain.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoreLoginCountRepository extends JpaRepository<MoreLoginCount, Integer> {
  public List<MoreLoginCount> findByExpiresAtBefore(LocalDateTime dateTime);

  List<MoreLoginCount> findByUser(User user);

}
