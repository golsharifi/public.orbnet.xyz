package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor // <-- Default constructor
@AllArgsConstructor // <-- Constructor with all fields
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @OneToOne
    private UnverifiedUser user;

    @Column(unique = true)
    private String verificationCode;

    @Column
    private LocalDateTime expiryDate;
}
