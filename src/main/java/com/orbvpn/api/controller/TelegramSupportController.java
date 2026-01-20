package com.orbvpn.api.controller;

import com.orbvpn.api.domain.entity.TelegramMessage;
import com.orbvpn.api.repository.TelegramMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@RestController
@RequestMapping("/api/telegram/support")
@RequiredArgsConstructor
@Secured(ADMIN)
public class TelegramSupportController {
    private final TelegramMessageRepository messageRepository;

    @GetMapping("/messages")
    public Page<TelegramMessage> getMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return messageRepository.findAllByOrderByTimestampDesc(
                PageRequest.of(page, size, Sort.by("timestamp").descending()));
    }
}