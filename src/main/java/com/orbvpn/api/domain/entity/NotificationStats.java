package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.NotificationCategory;
import com.orbvpn.api.domain.enums.NotificationChannel;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Data;

@Entity
@Data
@Table(name = "notification_stats")
public class NotificationStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel")
    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;

    @Column(name = "category")
    @Enumerated(EnumType.STRING)
    private NotificationCategory category;

    @Column(name = "sent_count")
    private int sentCount;

    @Column(name = "success_count")
    private int successCount;

    @Column(name = "fail_count")
    private int failCount;

    @Column(name = "stat_date")
    private LocalDate statDate;
}