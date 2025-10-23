package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.RadCheck;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RadCheckRepository extends JpaRepository<RadCheck, Integer> {
  long deleteByUsername(String username);

  Optional<RadCheck> findByUsernameAndAttribute(String username, String attribute);

  void deleteByUsernameAndAttribute(String username, String attribute);

  void deleteByUsernameAndAttributeAndValue(String username, String attribute, String value);

  List<RadCheck> findByAttribute(String attribute);

  List<RadCheck> findByAttributeAndUsername(String attribute, String userName);

  @Query(value = """
      SELECT username, attribute, COUNT(*) as count
      FROM radcheck
      WHERE attribute IN ('SHA-Password', 'Simultaneous-Use', 'Expiration')
      GROUP BY username, attribute
      HAVING COUNT(*) > 1
      ORDER BY username, attribute
      """, nativeQuery = true)
  List<Object[]> findDuplicateStats();

}
