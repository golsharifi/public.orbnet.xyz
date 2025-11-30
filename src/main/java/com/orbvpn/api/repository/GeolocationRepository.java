package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.Geolocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeolocationRepository extends JpaRepository<Geolocation, Integer> {
    Geolocation findByName(String name);

    Geolocation findByCountryCode(String countryCode);
}
