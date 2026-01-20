package com.orbvpn.api.controller;

import com.orbvpn.api.domain.dto.AppleReceiptRequest;
import com.orbvpn.api.domain.dto.AppleSubscriptionData;
import com.orbvpn.api.domain.dto.GooglePlaySubscriptionInfo;
import com.orbvpn.api.domain.dto.GooglePurchaseRequest;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.subscription.AppleService;
import com.orbvpn.api.service.subscription.GooglePlayService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/subscription")
public class SubscriptionVerificationController {

    private final AppleService appleService;
    private final GooglePlayService googlePlayService;
    private final UserService userService;

    @PostMapping("/verify/apple")
    public ResponseEntity<?> verifyAppleReceipt(
            @RequestBody AppleReceiptRequest request,
            @RequestHeader("X-Device-ID") String deviceId,
            Authentication authentication) {
        try {
            User user = userService.getUserFromAuthentication(authentication);
            AppleSubscriptionData data = appleService.getSubscriptionData(
                    request.getReceipt(),
                    deviceId,
                    user);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("Error verifying Apple receipt", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/verify/google")
    public ResponseEntity<?> verifyGooglePurchase(
            @RequestBody GooglePurchaseRequest request,
            @RequestHeader("X-Device-ID") String deviceId,
            Authentication authentication) {
        try {
            User user = userService.getUserFromAuthentication(authentication);
            GooglePlaySubscriptionInfo info = googlePlayService.verifyTokenWithGooglePlay(
                    request.getPackageName(),
                    request.getPurchaseToken(),
                    request.getSubscriptionId(),
                    deviceId,
                    user,
                    null);
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            log.error("Error verifying Google Play purchase", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}