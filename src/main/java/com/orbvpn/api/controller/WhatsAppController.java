package com.orbvpn.api.controller;

import com.orbvpn.api.service.notification.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
public class WhatsAppController {
    private final WhatsAppService whatsAppService;

    @GetMapping("/qr")
    @PreAuthorize("hasRole('ADMIN')")
    public String getQrCode() {
        return whatsAppService.getQrCode();
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean getStatus() {
        return whatsAppService.isConnected();
    }

    @PostMapping("/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public void disconnect() {
        whatsAppService.disconnect();
    }
}