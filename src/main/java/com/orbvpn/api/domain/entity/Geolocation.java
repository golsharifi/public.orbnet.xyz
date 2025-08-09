package com.orbvpn.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Geolocation {
  @Id
  private int id;

  @Column
  private String name;

  @Column
  private String code;

  @Column
  private String threeCharCode;

  @Column
  private String GeoDiscount;
}
