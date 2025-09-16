package com.orbvpn.api.domain.entity;

import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@Entity
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(indexes = {
        @Index(name = "idx_txn_id", columnList = "txnId"),
        @Index(name = "idx_status", columnList = "status")
})
public class CoinPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "none"))
    private User user;

    @OneToOne
    @JoinColumn(name = "payment_id", foreignKey = @ForeignKey(name = "none"))
    private Payment payment;

    @Column
    private String txnId;

    @Column
    private Boolean status;

    @Column
    private String coin;

    @Column
    private String coinAmount;

    @Column
    private String address;

    @Column
    private String confirms_needed;

    @Column
    private Integer timeout;

    @Column
    private String checkout_url;

    @Column
    private String status_url;

    @Column
    private String qrcode_url;

    @Column
    private String ipnUrl;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}