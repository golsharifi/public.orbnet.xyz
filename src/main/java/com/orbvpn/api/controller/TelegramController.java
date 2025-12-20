package com.orbvpn.api.controller;

import com.orbvpn.api.domain.dto.TelegramRegistration;
import com.orbvpn.api.service.notification.TelegramService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
public class TelegramController {
    private final TelegramService telegramService;

    @PostMapping("/register")
    public void registerTelegram(@RequestBody TelegramRegistration registration) {
        telegramService.registerUser(registration);
    }
}