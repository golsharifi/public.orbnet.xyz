// src/main/java/com/orbvpn/api/domain/entity/Geolocation.java
package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "geolocations")
public class Geolocation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "name")
  private String name; // e.g., "Netherlands"

  @Column(name = "country_code")
  private String countryCode; // e.g., "NL"

  @Column(name = "region")
  private String region; // ADD THIS if it doesn't exist: e.g., "Europe"

  @Column(name = "city")
  private String city;

  @Column(name = "latitude")
  private Double latitude;

  @Column(name = "longitude")
  private Double longitude;

  // ... other fields ...
}