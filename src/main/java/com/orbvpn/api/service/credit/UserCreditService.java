package com.orbvpn.api.service.credit;

import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.TokenTransactionType;
import com.orbvpn.api.exception.InsufficientTokensException;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.TokenBalanceRepository;
import com.orbvpn.api.repository.TokenTransactionRepository;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.service.audit.AdminAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing user credit/wallet operations.
 * Uses the existing TokenBalance and TokenTransaction infrastructure.
 * Credit is stored as tokens and can be used for purchases.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserCreditService {

    private final TokenBalanceRepository tokenBalanceRepository;
    private final TokenTransactionRepository tokenTransactionRepository;
    private final UserRepository userRepository;
    private final AdminAuditService adminAuditService;

    /**
     * Get user's current credit balance
     */
    @Transactional(readOnly = true)
    public BigDecimal getBalance(Integer userId) {
        TokenBalance balance = getOrCreateBalance(getUserById(userId));
        return balance.getBalance();
    }

    /**
     * Get user's TokenBalance object
     */
    @Transactional(readOnly = true)
    public TokenBalance getTokenBalance(Integer userId) {
        return getOrCreateBalance(getUserById(userId));
    }

    /**
     * Add credit to user's account from a payment gateway deposit
     * @param userId User ID
     * @param amount Amount to add
     * @param paymentGateway Payment gateway used (STRIPE, PAYPAL, etc.)
     * @param transactionId External transaction ID
     * @return Updated balance
     */
    @Transactional
    public TokenBalance depositCredit(Integer userId, BigDecimal amount, String paymentGateway, String transactionId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        User user = getUserById(userId);
        TokenBalance balance = getOrCreateBalance(user);

        // Create transaction record
        TokenTransaction transaction = new TokenTransaction();
        transaction.setUser(user);
        transaction.setAmount(amount);
        transaction.setType(TokenTransactionType.CREDIT_DEPOSIT);
        transaction.setAdVendor(paymentGateway); // Reuse field for payment source
        transaction.setRegion(transactionId); // Reuse field for transaction reference
        tokenTransactionRepository.save(transaction);

        // Update balance
        balance.setBalance(balance.getBalance().add(amount));
        balance.setLastActivityDate(LocalDateTime.now());
        TokenBalance savedBalance = tokenBalanceRepository.save(balance);

        log.info("Deposited {} credit to user {} via {}. New balance: {}",
                amount, userId, paymentGateway, savedBalance.getBalance());

        return savedBalance;
    }

    /**
     * Add credit by admin
     */
    @Transactional
    public TokenBalance adminAddCredit(Integer userId, BigDecimal amount, String reason) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }

        User user = getUserById(userId);
        TokenBalance balance = getOrCreateBalance(user);
        BigDecimal beforeBalance = balance.getBalance();

        // Create transaction record
        TokenTransaction transaction = new TokenTransaction();
        transaction.setUser(user);
        transaction.setAmount(amount);
        transaction.setType(TokenTransactionType.ADMIN_CREDIT);
        transaction.setRegion(reason); // Store reason
        tokenTransactionRepository.save(transaction);

        // Update balance
        balance.setBalance(balance.getBalance().add(amount));
        balance.setLastActivityDate(LocalDateTime.now());
        TokenBalance savedBalance = tokenBalanceRepository.save(balance);

        // Create audit log
        Map<String, Object> before = new HashMap<>();
        before.put("balance", beforeBalance);

        Map<String, Object> after = new HashMap<>();
        after.put("balance", savedBalance.getBalance());
        after.put("amountAdded", amount);
        after.put("reason", reason);

        adminAuditService.logUserAction(
                AdminAuditLog.ACTION_ADD_RESELLER_CREDIT, // Reuse action type
                user, before, after,
                String.format("Added %s credit to user. Reason: %s", amount, reason));

        log.info("Admin added {} credit to user {}. New balance: {}", amount, userId, savedBalance.getBalance());

        return savedBalance;
    }

    /**
     * Deduct credit by admin
     */
    @Transactional
    public TokenBalance adminDeductCredit(Integer userId, BigDecimal amount, String reason) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deduct amount must be positive");
        }

        User user = getUserById(userId);
        TokenBalance balance = getOrCreateBalance(user);
        BigDecimal beforeBalance = balance.getBalance();

        if (balance.getBalance().compareTo(amount) < 0) {
            throw new InsufficientTokensException("Insufficient credit balance");
        }

        // Create transaction record
        TokenTransaction transaction = new TokenTransaction();
        transaction.setUser(user);
        transaction.setAmount(amount.negate());
        transaction.setType(TokenTransactionType.ADMIN_DEBIT);
        transaction.setRegion(reason);
        tokenTransactionRepository.save(transaction);

        // Update balance
        balance.setBalance(balance.getBalance().subtract(amount));
        balance.setLastActivityDate(LocalDateTime.now());
        TokenBalance savedBalance = tokenBalanceRepository.save(balance);

        // Create audit log
        Map<String, Object> before = new HashMap<>();
        before.put("balance", beforeBalance);

        Map<String, Object> after = new HashMap<>();
        after.put("balance", savedBalance.getBalance());
        after.put("amountDeducted", amount);
        after.put("reason", reason);

        adminAuditService.logUserAction(
                AdminAuditLog.ACTION_DEDUCT_RESELLER_CREDIT,
                user, before, after,
                String.format("Deducted %s credit from user. Reason: %s", amount, reason));

        log.info("Admin deducted {} credit from user {}. New balance: {}", amount, userId, savedBalance.getBalance());

        return savedBalance;
    }

    /**
     * Spend credit for subscription purchase
     */
    @Transactional
    public TokenBalance spendForSubscription(Integer userId, BigDecimal amount, int groupId, String groupName) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Purchase amount must be positive");
        }

        User user = getUserById(userId);
        TokenBalance balance = getOrCreateBalance(user);

        if (balance.getBalance().compareTo(amount) < 0) {
            throw new InsufficientTokensException(
                    String.format("Insufficient credit. Required: %s, Available: %s",
                            amount, balance.getBalance()));
        }

        // Create transaction record
        TokenTransaction transaction = new TokenTransaction();
        transaction.setUser(user);
        transaction.setAmount(amount.negate());
        transaction.setType(TokenTransactionType.SUBSCRIPTION_PURCHASE);
        transaction.setAdVendor(String.valueOf(groupId)); // Store group ID
        transaction.setRegion(groupName); // Store group name
        tokenTransactionRepository.save(transaction);

        // Update balance
        balance.setBalance(balance.getBalance().subtract(amount));
        balance.setLastActivityDate(LocalDateTime.now());
        TokenBalance savedBalance = tokenBalanceRepository.save(balance);

        log.info("User {} spent {} credit for subscription {}. New balance: {}",
                userId, amount, groupName, savedBalance.getBalance());

        return savedBalance;
    }

    /**
     * Spend credit for device addon purchase
     */
    @Transactional
    public TokenBalance spendForDevices(Integer userId, BigDecimal amount, int deviceCount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Purchase amount must be positive");
        }

        User user = getUserById(userId);
        TokenBalance balance = getOrCreateBalance(user);

        if (balance.getBalance().compareTo(amount) < 0) {
            throw new InsufficientTokensException(
                    String.format("Insufficient credit. Required: %s, Available: %s",
                            amount, balance.getBalance()));
        }

        // Create transaction record
        TokenTransaction transaction = new TokenTransaction();
        transaction.setUser(user);
        transaction.setAmount(amount.negate());
        transaction.setType(TokenTransactionType.DEVICE_PURCHASE);
        transaction.setAdVendor(String.valueOf(deviceCount)); // Store device count
        tokenTransactionRepository.save(transaction);

        // Update balance
        balance.setBalance(balance.getBalance().subtract(amount));
        balance.setLastActivityDate(LocalDateTime.now());
        TokenBalance savedBalance = tokenBalanceRepository.save(balance);

        log.info("User {} spent {} credit for {} devices. New balance: {}",
                userId, amount, deviceCount, savedBalance.getBalance());

        return savedBalance;
    }

    /**
     * Add credit from gift card redemption
     */
    @Transactional
    public TokenBalance redeemGiftCard(Integer userId, BigDecimal amount, String giftCardCode) {
        User user = getUserById(userId);
        TokenBalance balance = getOrCreateBalance(user);

        // Create transaction record
        TokenTransaction transaction = new TokenTransaction();
        transaction.setUser(user);
        transaction.setAmount(amount);
        transaction.setType(TokenTransactionType.GIFT_CARD_REDEEM);
        transaction.setRegion(giftCardCode); // Store gift card code
        tokenTransactionRepository.save(transaction);

        // Update balance
        balance.setBalance(balance.getBalance().add(amount));
        balance.setLastActivityDate(LocalDateTime.now());
        TokenBalance savedBalance = tokenBalanceRepository.save(balance);

        log.info("User {} redeemed gift card {} for {} credit. New balance: {}",
                userId, giftCardCode, amount, savedBalance.getBalance());

        return savedBalance;
    }

    /**
     * Process a refund to user's credit
     */
    @Transactional
    public TokenBalance refundCredit(Integer userId, BigDecimal amount, String reason) {
        User user = getUserById(userId);
        TokenBalance balance = getOrCreateBalance(user);

        // Create transaction record
        TokenTransaction transaction = new TokenTransaction();
        transaction.setUser(user);
        transaction.setAmount(amount);
        transaction.setType(TokenTransactionType.REFUND);
        transaction.setRegion(reason);
        tokenTransactionRepository.save(transaction);

        // Update balance
        balance.setBalance(balance.getBalance().add(amount));
        balance.setLastActivityDate(LocalDateTime.now());
        TokenBalance savedBalance = tokenBalanceRepository.save(balance);

        log.info("Refunded {} credit to user {}. Reason: {}. New balance: {}",
                amount, userId, reason, savedBalance.getBalance());

        return savedBalance;
    }

    /**
     * Check if user has enough credit
     */
    public boolean hasEnoughCredit(Integer userId, BigDecimal amount) {
        TokenBalance balance = getOrCreateBalance(getUserById(userId));
        return balance.getBalance().compareTo(amount) >= 0;
    }

    // Helper methods

    private User getUserById(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
    }

    private TokenBalance getOrCreateBalance(User user) {
        return tokenBalanceRepository.findByUser_Id(user.getId())
                .orElseGet(() -> {
                    TokenBalance newBalance = new TokenBalance();
                    newBalance.setUser(user);
                    newBalance.setBalance(BigDecimal.ZERO);
                    return tokenBalanceRepository.save(newBalance);
                });
    }
}
