package com.orbvpn.api.service.giftcard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.orbvpn.api.repository.GiftCardRepository;
import com.orbvpn.api.exception.GiftCardException;

import java.security.SecureRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GiftCardCodeGenerator {
    private static final int CODE_LENGTH = 12; // Length of random part
    private static final String PREFIX = "ORB";
    private static final String SEPARATOR = "-";
    private static final int MAX_ATTEMPTS = 10;
    private static final SecureRandom secureRandom = new SecureRandom();

    private final GiftCardRepository giftCardRepository;
    private final ReentrantLock lock = new ReentrantLock();
    private final Set<String> generatedCodes = new HashSet<>();

    /**
     * Generates a guaranteed unique gift card code
     * Thread-safe and uses multiple layers of uniqueness checking
     */
    public String generateUniqueCode() {
        lock.lock();
        try {
            String code;
            int attempts = 0;

            while (attempts < MAX_ATTEMPTS) {
                code = generateCode();

                // Check in-memory cache
                if (generatedCodes.contains(code)) {
                    attempts++;
                    continue;
                }

                // Check database
                if (giftCardRepository.findByCode(code).isPresent()) {
                    attempts++;
                    continue;
                }

                // If we get here, the code is unique
                generatedCodes.add(code);
                log.debug("Generated unique gift card code: {} (attempt {})", code, attempts + 1);
                return code;
            }

            log.error("Failed to generate unique code after {} attempts", MAX_ATTEMPTS);
            throw new GiftCardException("Unable to generate unique gift card code after " + MAX_ATTEMPTS + " attempts");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Generates a single gift card code with enhanced randomness
     */
    public String generateCode() { // Changed from private to public
        // Use SecureRandom for better randomness
        StringBuilder randomPart = new StringBuilder();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        for (int i = 0; i < CODE_LENGTH; i++) {
            int randomIndex = secureRandom.nextInt(chars.length());
            randomPart.append(chars.charAt(randomIndex));
        }

        String formattedCode = formatCode(randomPart.toString());
        return PREFIX + SEPARATOR + formattedCode;
    }

    /**
     * Format the random string into sections of 4 characters
     */
    private String formatCode(String code) {
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < code.length(); i += 4) {
            if (i > 0) {
                formatted.append(SEPARATOR);
            }
            formatted.append(code.substring(i, Math.min(i + 4, code.length())));
        }
        return formatted.toString();
    }

    /**
     * Validates the format of a gift card code
     */
    public boolean isValidFormat(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }

        code = code.trim();
        String pattern = "^" + PREFIX + "\\" + SEPARATOR + "[A-Z0-9]{4}\\" +
                SEPARATOR + "[A-Z0-9]{4}\\" + SEPARATOR + "[A-Z0-9]{4}$";
        return code.matches(pattern);
    }

    /**
     * Normalize a gift card code
     */
    public String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        return code.replaceAll("\\s+", "").toUpperCase();
    }

    /**
     * Remove a code from the in-memory cache
     */
    public void releaseCode(String code) {
        lock.lock();
        try {
            generatedCodes.remove(code);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clear the in-memory cache
     */
    public void clearCodeCache() {
        lock.lock();
        try {
            generatedCodes.clear();
            log.info("Cleared gift card code cache");
        } finally {
            lock.unlock();
        }
    }
}