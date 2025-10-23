package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import com.orbvpn.api.domain.enums.ProtocolType;

@Data
@Entity
@Table(name = "mining_server_protocols")
public class MiningServerProtocol {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "mining_server_id")
    private MiningServer miningServer;

    @Enumerated(EnumType.STRING)
    private ProtocolType type;

    private Integer port;
    private Boolean enabled = true;
    private String publicKey;
    private String configTemplate;
}