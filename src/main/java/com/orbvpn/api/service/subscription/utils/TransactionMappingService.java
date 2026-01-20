package com.orbvpn.api.service.subscription.utils;

import com.orbvpn.api.domain.entity.TransactionUserMapping;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.repository.TransactionUserMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionMappingService {
    private final TransactionUserMappingRepository transactionUserMappingRepository;

    /**
     * Ensures a mapping exists for the given user, token, and gateway.
     * Checks both by token and by email+gateway to prevent duplicate entries.
     */
    @Transactional
    public void ensureMapping(User user, String token, GatewayName gateway) {
        try {
            // First, check if mapping exists for this email and gateway
            // This check is critical to prevent the duplicate key error
            Optional<TransactionUserMapping> existingEmailMapping = transactionUserMappingRepository
                    .findFirstByEmailAndGateway(user.getEmail(), gateway);

            if (existingEmailMapping.isPresent()) {
                // Update the existing mapping rather than creating a new one
                TransactionUserMapping mapping = existingEmailMapping.get();
                // Only update transaction ID if it's changed
                if (!token.equals(mapping.getTransactionId())) {
                    mapping.setTransactionId(token);
                    transactionUserMappingRepository.save(mapping);
                    log.info("Updated existing email mapping for user: {} with new token: {}",
                            user.getEmail(), token);
                }
                return;
            }

            // If no email mapping exists, check if mapping exists for this token
            Optional<TransactionUserMapping> existingTokenMapping = transactionUserMappingRepository
                    .findByTransactionIdAndGateway(token, gateway);

            if (existingTokenMapping.isPresent()) {
                // Update mapping if user is different
                TransactionUserMapping mapping = existingTokenMapping.get();
                if (mapping.getUser() == null || mapping.getUser().getId() != user.getId()) {
                    mapping.setUser(user);
                    mapping.setEmail(user.getEmail());
                    transactionUserMappingRepository.save(mapping);
                    log.info("Updated existing token mapping for user: {} with token: {}",
                            user.getEmail(), token);
                }
                return;
            }

            // If no mapping exists at all, create a new one
            TransactionUserMapping newMapping = new TransactionUserMapping();
            newMapping.setUser(user);
            newMapping.setEmail(user.getEmail());
            newMapping.setTransactionId(token);
            newMapping.setGateway(gateway);
            transactionUserMappingRepository.save(newMapping);
            log.info("Created new transaction mapping for user: {} with token: {}",
                    user.getEmail(), token);
        } catch (Exception e) {
            log.error("Error creating/updating transaction mapping: {}", e.getMessage(), e);
            // Don't throw to allow the subscription to proceed
        }
    }

    /**
     * Finds a user by their transaction token and gateway.
     */
    @Transactional(readOnly = true)
    public User findUserByToken(String token, GatewayName gateway) {
        try {
            Optional<TransactionUserMapping> mapping = transactionUserMappingRepository
                    .findByTransactionIdAndGateway(token, gateway);

            if (mapping.isPresent() && mapping.get().getUser() != null) {
                return mapping.get().getUser();
            }

            // If direct mapping not found but email is present, try by email
            if (mapping.isPresent() && mapping.get().getEmail() != null) {
                Optional<TransactionUserMapping> emailMapping = transactionUserMappingRepository
                        .findFirstByEmailAndGateway(mapping.get().getEmail(), gateway);

                if (emailMapping.isPresent() && emailMapping.get().getUser() != null) {
                    return emailMapping.get().getUser();
                }
            }

            log.warn("No user mapping found for token: {} and gateway: {}", token, gateway);
            return null;
        } catch (Exception e) {
            log.error("Error finding user by token: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Checks if a user has any existing transaction mapping.
     * Used for trial eligibility determination.
     */
    @Transactional(readOnly = true)
    public boolean hasExistingMapping(String email, GatewayName gateway) {
        try {
            return transactionUserMappingRepository.findFirstByEmailAndGateway(email, gateway).isPresent();
        } catch (Exception e) {
            log.error("Error checking existing mapping: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Checks if a user has any transaction mapping across any gateway.
     * Used for general trial eligibility determination.
     */
    @Transactional(readOnly = true)
    public boolean hasAnyMappingForEmail(String email) {
        try {
            return transactionUserMappingRepository.countByEmail(email) > 0;
        } catch (Exception e) {
            log.error("Error checking any mapping for email: {}", e.getMessage(), e);
            return false;
        }
    }
}