package com.orbvpn.api.service.giftcard;

import com.orbvpn.api.domain.dto.GiftCardCreate;
import com.orbvpn.api.domain.dto.GiftCardView;
import com.orbvpn.api.domain.entity.GiftCard;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.exception.BadRequestException;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.mapper.GiftCardCreateMapper;
import com.orbvpn.api.mapper.GiftCardViewMapper;
import com.orbvpn.api.repository.GiftCardRepository;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.RadiusService;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import com.orbvpn.api.service.subscription.RenewUserSubscriptionService;
import com.orbvpn.api.service.AsyncNotificationHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GiftCardService {
    private final GiftCardRepository giftCardRepository;
    private final GiftCardCodeGenerator codeGenerator;
    private final GroupService groupService;
    private final UserService userService;
    private final RenewUserSubscriptionService renewUserSubscriptionService;
    private final GiftCardCreateMapper giftCardCreateMapper;
    private final GiftCardViewMapper giftCardViewMapper;
    private final AsyncNotificationHelper asyncNotificationHelper;
    private final UserSubscriptionService userSubscriptionService;
    private final RadiusService radiusService;

    /**
     * Creates a single gift card
     */
    public GiftCardView createGiftCard(GiftCardCreate giftCardCreate) {
        Group group = groupService.getById(giftCardCreate.getGroupId());

        // Create gift card and set its properties
        GiftCard giftCard = giftCardCreateMapper.create(giftCardCreate);
        giftCard.setGroup(group);
        giftCard.setAmount(group.getPrice()); // Get price from the group
        giftCard.setCode(generateUniqueCode());
        giftCard.setExpirationDate(LocalDateTime.now().plusDays(giftCardCreate.getValidityDays()));

        GiftCard savedGiftCard = giftCardRepository.save(giftCard);
        log.info("Created new gift card with code: {} for group: {}, amount: {}",
                savedGiftCard.getCode(),
                group.getName(),
                savedGiftCard.getAmount());

        return giftCardViewMapper.toView(savedGiftCard);
    }

    /**
     * Creates multiple gift cards
     */
    public List<GiftCardView> createBulkGiftCards(GiftCardCreate giftCardCreate, int count) {
        List<GiftCard> giftCards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            GiftCardView giftCardView = createGiftCard(giftCardCreate);
            giftCards.add(giftCardRepository.findById(giftCardView.getId())
                    .orElseThrow(() -> new RuntimeException("Failed to retrieve created gift card")));
        }

        log.info("Created {} gift cards for group ID: {}", count, giftCardCreate.getGroupId());

        return giftCards.stream()
                .map(giftCardViewMapper::toView)
                .collect(Collectors.toList());
    }

    /**
     * Redeems a gift card and creates subscription for user
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public GiftCardView redeemGiftCard(String code) {
        User currentUser = userService.getUser();
        String normalizedCode = codeGenerator.normalizeCode(code);

        if (!codeGenerator.isValidFormat(normalizedCode)) {
            log.warn("Invalid gift card code format attempted: {}", code);
            throw new BadRequestException("Invalid gift card code format");
        }

        // Use pessimistic locking to prevent concurrent redemption
        GiftCard giftCard = giftCardRepository.findByCodeWithLock(normalizedCode)
                .orElseThrow(() -> new NotFoundException("Gift card not found"));

        // Double-check if the card was used during lock acquisition
        if (giftCard.isUsed() || giftCard.isCancelled()) {
            log.warn("Attempt to use invalid gift card: {} (used: {}, cancelled: {})",
                    code, giftCard.isUsed(), giftCard.isCancelled());
            throw new BadRequestException("Gift card is no longer valid");
        }

        validateGiftCard(giftCard);

        try {
            // Mark gift card as used
            giftCard.setUsed(true);
            giftCard.setRedeemedBy(currentUser);
            giftCard.setRedeemedAt(LocalDateTime.now());

            // Save immediately to prevent concurrent redemption
            giftCard = giftCardRepository.saveAndFlush(giftCard);

            // Convert gift card to subscription
            UserSubscription currentSubscription = userSubscriptionService.getCurrentSubscription(currentUser);
            LocalDateTime expiresAt;

            if (currentSubscription != null && currentSubscription.getExpiresAt() != null
                    && currentSubscription.getExpiresAt().isAfter(LocalDateTime.now())) {
                expiresAt = currentSubscription.getExpiresAt().plusDays(giftCard.getGroup().getDuration());
                log.info("Extending subscription for user: {} from {} to {}",
                        currentUser.getEmail(), currentSubscription.getExpiresAt(), expiresAt);
                currentSubscription.setMultiLoginCount(giftCard.getGroup().getMultiLoginCount());
                radiusService.editUserMoreLoginCount(currentUser, giftCard.getGroup().getMultiLoginCount());

            } else {
                expiresAt = LocalDateTime.now().plusDays(giftCard.getGroup().getDuration());
                log.info("Creating new subscription for user: {} until {}",
                        currentUser.getEmail(), expiresAt);
            }

            renewUserSubscriptionService.assignSubscription(
                    currentUser,
                    giftCard.getGroup().getId(),
                    expiresAt,
                    GatewayName.GIFT_CARD,
                    false,
                    giftCard.getCode(), // token
                    false, // isTrialPeriod
                    giftCard.getCode() // subscriptionId - use gift card code as the ID
            );

            // Send notification asynchronously
            asyncNotificationHelper.sendGiftCardRedemptionNotificationAsync(currentUser, giftCard);

            log.info("Successfully redeemed gift card: {} for user: {}", code, currentUser.getEmail());
            return giftCardViewMapper.toView(giftCard);

        } catch (Exception e) {
            log.error("Error redeeming gift card: {} for user: {}", code, currentUser.getEmail(), e);
            throw new RuntimeException("Failed to redeem gift card: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteGiftCard(Long id) {
        GiftCard giftCard = giftCardRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Gift card not found"));

        if (giftCard.isUsed()) {
            throw new BadRequestException("Cannot delete a used gift card");
        }

        if (giftCard.isCancelled()) {
            throw new BadRequestException("Gift card is already cancelled");
        }

        log.info("Deleting unused gift card: {}", giftCard.getCode());
        giftCardRepository.delete(giftCard);
    }

    @Transactional
    public GiftCardView cancelGiftCard(Long id) {
        GiftCard giftCard = giftCardRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Gift card not found"));

        if (giftCard.isUsed()) {
            throw new BadRequestException("Gift card is already used");
        }

        if (giftCard.isCancelled()) {
            throw new BadRequestException("Gift card is already cancelled");
        }

        User admin = userService.getUser();
        giftCard.setCancelled(true);
        giftCard.setCancelledBy(admin);
        giftCard.setCancelledAt(LocalDateTime.now());

        log.info("Cancelled gift card: {} by admin: {}", giftCard.getCode(), admin.getEmail());
        return giftCardViewMapper.toView(giftCardRepository.save(giftCard));
    }

    @Transactional
    public List<GiftCardView> cancelGiftCards(List<Long> ids) {
        List<GiftCard> giftCards = giftCardRepository.findAllById(ids);
        List<GiftCard> cancelledCards = new ArrayList<>();
        User admin = userService.getUser();

        for (GiftCard giftCard : giftCards) {
            if (!giftCard.isUsed() && !giftCard.isCancelled()) {
                giftCard.setCancelled(true);
                giftCard.setCancelledBy(admin);
                giftCard.setCancelledAt(LocalDateTime.now());
                cancelledCards.add(giftCardRepository.save(giftCard));
            }
        }

        log.info("Bulk cancelled {} gift cards by admin: {}",
                cancelledCards.size(), admin.getEmail());

        return cancelledCards.stream()
                .map(giftCardViewMapper::toView)
                .collect(Collectors.toList());
    }

    /**
     * Validates a gift card for redemption
     */
    private void validateGiftCard(GiftCard giftCard) {
        if (giftCard.isUsed()) {
            log.warn("Attempt to use already redeemed gift card: {}", giftCard.getCode());
            throw new BadRequestException("Gift card has already been used");
        }

        if (giftCard.getExpirationDate().isBefore(LocalDateTime.now())) {
            log.warn("Attempt to use expired gift card: {}", giftCard.getCode());
            throw new BadRequestException("Gift card has expired");
        }
    }

    /**
     * Generates a unique gift card code
     */
    private String generateUniqueCode() {
        String code;
        int maxAttempts = 5;
        int attempts = 0;

        do {
            code = codeGenerator.generateCode();
            attempts++;

            if (attempts >= maxAttempts) {
                log.error("Failed to generate unique gift card code after {} attempts", maxAttempts);
                throw new RuntimeException("Unable to generate unique gift card code");
            }
        } while (giftCardRepository.findByCode(code).isPresent());

        log.debug("Generated unique gift card code: {}", code);
        return code;
    }

    /**
     * Gets all valid (unused and not expired) gift cards
     */
    public List<GiftCardView> getValidGiftCards() {
        return giftCardRepository.findByUsedFalseAndExpirationDateAfter(LocalDateTime.now())
                .stream()
                .map(giftCardViewMapper::toView)
                .collect(Collectors.toList());
    }

    /**
     * Gets all gift cards for a specific group
     */
    public List<GiftCardView> getGiftCardsByGroup(int groupId) {
        return giftCardRepository.findByGroupId(groupId)
                .stream()
                .map(giftCardViewMapper::toView)
                .collect(Collectors.toList());
    }

    /**
     * Gets a specific gift card by its code
     */
    public GiftCardView getGiftCardByCode(String code) {
        String normalizedCode = codeGenerator.normalizeCode(code);

        GiftCard giftCard = giftCardRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new NotFoundException("Gift card not found"));

        return giftCardViewMapper.toView(giftCard);
    }
}