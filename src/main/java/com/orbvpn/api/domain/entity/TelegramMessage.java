package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.MessageDirection;
import com.orbvpn.api.domain.enums.MessageStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "telegram_messages")
public class TelegramMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;

    @Column(columnDefinition = "TEXT")
    private String message;

    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    private MessageDirection direction;

    @Enumerated(EnumType.STRING)
    private MessageStatus status;

    private String adminUsername;

    // This will be used for the GraphQL response
    public String getUserId() {
        return user != null ? String.valueOf(user.getId()) : null;
    }
}