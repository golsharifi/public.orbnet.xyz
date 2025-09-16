package com.orbvpn.api.domain.dto;

public class UserMigrationDto {
    private Integer id;
    private String email;
    private String uuid;

    public UserMigrationDto() {
    }

    public UserMigrationDto(Integer id, String email, String uuid) {
        this.id = id;
        this.email = email;
        this.uuid = uuid;
    }

    // Getters and setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}