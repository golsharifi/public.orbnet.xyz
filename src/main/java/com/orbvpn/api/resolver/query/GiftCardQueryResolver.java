package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.GiftCardView;
import com.orbvpn.api.service.giftcard.GiftCardService;
import com.orbvpn.api.exception.NotFoundException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GiftCardQueryResolver {
    private final GiftCardService giftCardService;

    @Secured(ADMIN)
    @QueryMapping
    public List<GiftCardView> getValidGiftCards() {
        log.info("Fetching valid gift cards");
        try {
            List<GiftCardView> cards = giftCardService.getValidGiftCards();
            log.info("Successfully retrieved {} valid gift cards", cards.size());
            return cards;
        } catch (Exception e) {
            log.error("Error fetching valid gift cards - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<GiftCardView> getGiftCardsByGroup(
            @Argument @Valid @Positive(message = "Group ID must be positive") Integer groupId) {
        log.info("Fetching gift cards for group: {}", groupId);
        try {
            List<GiftCardView> cards = giftCardService.getGiftCardsByGroup(groupId);
            log.info("Successfully retrieved {} gift cards for group: {}", cards.size(), groupId);
            return cards;
        } catch (Exception e) {
            log.error("Error fetching gift cards for group: {} - Error: {}", groupId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, USER })
    @QueryMapping
    public GiftCardView getGiftCardByCode(
            @Argument @Valid @NotBlank(message = "Gift card code cannot be empty") String code) {
        log.info("Fetching gift card by code");
        try {
            GiftCardView card = giftCardService.getGiftCardByCode(code);
            if (card == null) {
                throw new NotFoundException("Gift card not found with code: " + code);
            }
            log.info("Successfully retrieved gift card by code");
            return card;
        } catch (NotFoundException e) {
            log.warn("Gift card not found - Code: {} - Error: {}", code, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error fetching gift card by code - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}