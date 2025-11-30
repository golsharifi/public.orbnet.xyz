package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WhatsAppStatus {
    private boolean connected;
    private String qrCode;
    private String qrCodeSvg;
    private Integer qrCodeAge; // Using Integer
    private Boolean qrCodeExpired;

    // Constructor for backwards compatibility
    public WhatsAppStatus(boolean connected, String qrCode) {
        this.connected = connected;
        this.qrCode = qrCode;
        this.qrCodeSvg = null;
        this.qrCodeAge = null;
        this.qrCodeExpired = null;
    }

    // Constructor with QR code SVG
    public WhatsAppStatus(boolean connected, String qrCode, String qrCodeSvg) {
        this.connected = connected;
        this.qrCode = qrCode;
        this.qrCodeSvg = qrCodeSvg;
        this.qrCodeAge = null;
        this.qrCodeExpired = null;
    }

    // Constructor with age and expiry info - accepts int
    public WhatsAppStatus(boolean connected, String qrCode, String qrCodeSvg, int qrCodeAge, boolean qrCodeExpired) {
        this.connected = connected;
        this.qrCode = qrCode;
        this.qrCodeSvg = qrCodeSvg;
        this.qrCodeAge = qrCodeAge; // Direct assignment since it's already int
        this.qrCodeExpired = qrCodeExpired;
    }
}