package com.orbvpn.api.domain.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bridge_connection_logs", indexes = {
    @Index(name = "idx_bridge_log_user_time", columnList = "user_id, connected_at"),
    @Index(name = "idx_bridge_log_bridge_time", columnList = "bridge_server_id, connected_at"),
    @Index(name = "idx_bridge_log_exit_time", columnList = "exit_server_id, connected_at")
})
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class BridgeConnectionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "bridge_server_id", nullable = false)
    private Long bridgeServerId;

    @Column(name = "exit_server_id", nullable = false)
    private Long exitServerId;

    @Column(nullable = false, length = 20)
    private String protocol;

    @Column(name = "connected_at")
    @CreatedDate
    private LocalDateTime connectedAt;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    @Column(name = "bytes_sent")
    private Long bytesSent = 0L;

    @Column(name = "bytes_received")
    private Long bytesReceived = 0L;

    @Column(name = "session_duration_seconds")
    private Integer sessionDurationSeconds = 0;

    @Column(length = 20)
    private String status = "connected";

    public BridgeConnectionLog(User user, Long bridgeServerId, Long exitServerId, String protocol) {
        this.user = user;
        this.bridgeServerId = bridgeServerId;
        this.exitServerId = exitServerId;
        this.protocol = protocol;
        this.status = "connected";
    }

    public void disconnect() {
        this.disconnectedAt = LocalDateTime.now();
        this.status = "disconnected";
        if (this.connectedAt != null) {
            this.sessionDurationSeconds = (int) java.time.Duration.between(this.connectedAt, this.disconnectedAt).getSeconds();
        }
    }
}
