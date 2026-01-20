package com.orbvpn.api.domain.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_bridge_settings")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserBridgeSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Boolean enabled = false;

    @Column(name = "selected_bridge_id")
    private Long selectedBridgeId;

    @Column(name = "auto_select", nullable = false)
    private Boolean autoSelect = true;

    @Column(name = "last_used_bridge_id")
    private Long lastUsedBridgeId;

    @Column(name = "created_at")
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;

    public UserBridgeSettings(User user) {
        this.user = user;
        this.userId = (long) user.getId();
        this.enabled = false;
        this.autoSelect = true;
    }
}
