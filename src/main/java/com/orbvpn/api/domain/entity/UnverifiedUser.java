package com.orbvpn.api.domain.entity;

import lombok.Data;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import java.util.Locale;

import com.orbvpn.api.domain.entity.converter.LocaleConverter;

@Entity
@Data
public class UnverifiedUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, unique = true)
    @Email
    private String email;

    @Column
    private String password;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "aes_key", nullable = true)
    private String aesKey;

    @Column(name = "aes_iv", nullable = true)
    private String aesIv;

    @Column
    @Convert(converter = LocaleConverter.class)
    private Locale language;

    // Getter and Setter for language
    public Locale getLanguage() {
        return language;
    }

    public void setLanguage(Locale language) {
        this.language = language;
    }

    // Lombok's @Data should automatically generate the getters and setters for you.
}
