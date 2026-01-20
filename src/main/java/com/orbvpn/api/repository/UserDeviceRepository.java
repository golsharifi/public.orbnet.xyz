package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    List<UserDevice> getUserDeviceByUser(User user);

    Optional<UserDevice> findFirstByDeviceId(String deviceId);

    Optional<UserDevice> getUserDeviceById(Long id);

    void deleteByUser(User user); // This will delete all devices for the given user

}
