package com.orbvpn.api.service.subscription.payment;

import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.Reseller;
import com.orbvpn.api.domain.enums.ResellerLevelName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Slf4j
public class PriceCalculator {

    public BigDecimal calculatePrice(Reseller reseller, Group group, int days) {
        // If owner, price is zero
        if (reseller.getLevel().getName() == ResellerLevelName.OWNER) {
            return BigDecimal.ZERO;
        }

        // For regular resellers, apply discount
        BigDecimal basePrice = group.getPrice();
        BigDecimal discount = basePrice.multiply(reseller.getLevel().getDiscountPercent())
                .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);

        BigDecimal discountedPrice = basePrice.subtract(discount);

        // Calculate price based on days ratio
        BigDecimal daysRatio = BigDecimal.valueOf(days)
                .divide(BigDecimal.valueOf(group.getDuration()), 2, RoundingMode.HALF_UP);

        return discountedPrice.multiply(daysRatio).setScale(2, RoundingMode.HALF_UP);
    }
}