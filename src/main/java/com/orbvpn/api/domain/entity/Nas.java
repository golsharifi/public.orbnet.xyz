package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.ServerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Nas {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @Column(name = "nasname", unique = true)
  private String nasName;

  @Column(name = "shortname")
  private String shortName;

  @Column
  @Enumerated(EnumType.STRING)
  private ServerType type;

  @Column
  private Integer ports;

  @Column
  private String server;

  @Column(nullable = false)
  private String secret;

  @Column
  private String community;

  @Column
  private String description;
}
