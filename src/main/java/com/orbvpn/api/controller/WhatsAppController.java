package com.orbvpn.api.controller;

import com.orbvpn.api.service.notification.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
public class WhatsAppController {
    private final WhatsAppService whatsAppService;

    @GetMapping("/qr")
    @Secured(ADMIN)
    public String getQrCode() {
        return whatsAppService.getQrCode();
    }

    @GetMapping("/status")
    @Secured(ADMIN)
    public boolean getStatus() {
        return whatsAppService.isConnected();
    }

    @PostMapping("/disconnect")
    @Secured(ADMIN)
    public void disconnect() {
        whatsAppService.disconnect();
    }
}