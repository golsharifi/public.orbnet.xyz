package com.orbvpn.api.domain.dto;

public class SignupResponse {
    private String message;
    private Boolean success;

    public SignupResponse(String message, Boolean success) {
        this.message = message;
        this.success = success;
    }

    // Getter and Setter methods
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }
}
