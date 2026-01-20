package com.orbvpn.api.domain.entity;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Getter
@Setter
@Entity
public class ResellerScoreLimit {

    @Id
    private int id;

    @Column
    private String scoreDefinition;

    @Column(unique = true)
    private String symbol;

    @Column
    private Integer maximumLimit;

}
