package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.GiftCardCreate;
import com.orbvpn.api.domain.dto.GiftCardView;
import com.orbvpn.api.service.giftcard.GiftCardService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GiftCardMutationResolver {
    private final GiftCardService giftCardService;

    @Secured(ADMIN)
    @MutationMapping
    public GiftCardView generateGiftCard(@Argument @Valid GiftCardCreate input) {
        log.info("Generating gift card");
        try {
            return giftCardService.createGiftCard(input);
        } catch (Exception e) {
            log.error("Error generating gift card - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public List<GiftCardView> generateBulkGiftCards(
            @Argument("input") @Valid GiftCardCreate input,
            @Argument @Valid @Min(value = 1, message = "Count must be at least 1") int count) {
        log.info("Generating {} gift cards", count);
        try {
            if (input == null) {
                throw new IllegalArgumentException("Gift card input cannot be null");
            }
            return giftCardService.createBulkGiftCards(input, count);
        } catch (Exception e) {
            log.error("Error generating bulk gift cards - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ USER, ADMIN })
    @MutationMapping
    public GiftCardView redeemGiftCard(
            @Argument @Valid @NotBlank(message = "Gift card code is required") String code) {
        log.info("Redeeming gift card: {}", code);
        try {
            return giftCardService.redeemGiftCard(code);
        } catch (Exception e) {
            log.error("Error redeeming gift card: {} - Error: {}", code, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public boolean deleteGiftCard(
            @Argument @Valid @Positive(message = "Gift card ID must be positive") Long id) {
        log.info("Deleting gift card: {}", id);
        try {
            giftCardService.deleteGiftCard(id);
            return true;
        } catch (Exception e) {
            log.error("Error deleting gift card: {} - Error: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public GiftCardView cancelGiftCard(
            @Argument @Valid @Positive(message = "Gift card ID must be positive") Long id) {
        log.info("Cancelling gift card: {}", id);
        try {
            return giftCardService.cancelGiftCard(id);
        } catch (Exception e) {
            log.error("Error cancelling gift card: {} - Error: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public List<GiftCardView> cancelGiftCards(@Argument List<@Valid @Positive Long> ids) {
        log.info("Cancelling gift cards: {}", ids);
        try {
            return giftCardService.cancelGiftCards(ids);
        } catch (Exception e) {
            log.error("Error cancelling gift cards: {} - Error: {}", ids, e.getMessage(), e);
            throw e;
        }
    }
}