package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.CouponCodeDto;
import com.orbvpn.api.domain.entity.CouponCode;
import com.orbvpn.api.service.CouponCodeService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CouponCodeMutation {
    private final CouponCodeService couponCodeService;

    @Secured(ADMIN)
    @MutationMapping
    public CouponCode createCouponCode(@Argument @Valid CouponCodeDto couponCodeDto) {
        log.info("Creating coupon code");
        try {
            return couponCodeService.createCouponCode(couponCodeDto);
        } catch (Exception e) {
            log.error("Error creating coupon code - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public CouponCode updateCouponCode(@Argument @Valid CouponCodeDto couponCodeDto) {
        log.info("Updating coupon code");
        try {
            return couponCodeService.updateCouponCode(couponCodeDto);
        } catch (Exception e) {
            log.error("Error updating coupon code - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @MutationMapping
    public CouponCode checkCouponCode(
            @Argument @Valid @NotBlank(message = "Coupon code cannot be empty") String code) {
        log.info("Checking coupon code: {}", code);
        try {
            return couponCodeService.checkCouponCode(code);
        } catch (Exception e) {
            log.error("Error checking coupon code: {} - Error: {}", code, e.getMessage(), e);
            throw e;
        }
    }

    @MutationMapping
    public CouponCode useCouponCode(
            @Argument @Valid @NotBlank(message = "Coupon code cannot be empty") String code) {
        log.info("Using coupon code: {}", code);
        try {
            return couponCodeService.useCouponCode(code);
        } catch (Exception e) {
            log.error("Error using coupon code: {} - Error: {}", code, e.getMessage(), e);
            throw e;
        }
    }
}