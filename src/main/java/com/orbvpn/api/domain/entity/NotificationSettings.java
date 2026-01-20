package com.orbvpn.api.domain.entity;

import lombok.Data;
import jakarta.persistence.*;

@Entity
@Data
public class NotificationSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private int batchSize = 50;
    private int delayBetweenBatches = 60; // In seconds
}
