package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class RateLimit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String email;
    private long tokens;
    private LocalDateTime lastRefillTimestamp;

    public long getTokens() {
        return tokens;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setTokens(long tokens) {
        this.tokens = tokens;
    }

    public void setLastRefillTimestamp(LocalDateTime lastRefillTimestamp) {
        this.lastRefillTimestamp = lastRefillTimestamp;
    }

    public LocalDateTime getLastRefillTimestamp() {
        return lastRefillTimestamp;
    }

    // Other getters, setters, constructors, etc.
}
