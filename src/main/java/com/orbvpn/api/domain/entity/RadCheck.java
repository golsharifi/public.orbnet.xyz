package com.orbvpn.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Table(name = "radcheck")
@Entity
@Getter
@Setter
public class RadCheck {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @Column
  private String username;

  @Column
  private String attribute;

  @Column(columnDefinition = "CHAR(2)")
  private String op;

  @Column
  private String value;
}
