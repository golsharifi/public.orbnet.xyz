package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.GatewayName;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;

@Entity
@Getter
@Setter
@Table(name = "transaction_user_mappings")
public class TransactionUserMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "transaction_id", columnDefinition = "MEDIUMTEXT")
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway", nullable = false)
    private GatewayName gateway;

    @Column(nullable = false)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

}
