package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.ServiceGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GroupRepository extends JpaRepository<Group, Integer> {
  Long deleteByServiceGroup(ServiceGroup serviceGroup);

  List<Group> findAllByServiceGroup(ServiceGroup serviceGroup);

  List<Group> findAllByRegistrationGroupIsTrue();

  Optional<Group> findByName(String name);

  @Query(value = "select * from group_app where id = :id", nativeQuery = true)
  Group getGroupIgnoreDelete(int id);

}
