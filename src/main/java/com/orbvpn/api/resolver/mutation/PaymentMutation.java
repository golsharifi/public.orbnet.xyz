package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.enums.PaymentCategory;
import com.orbvpn.api.domain.payload.CoinPayment.*;
import com.orbvpn.api.domain.payload.NowPayment.NowPaymentResponse;
import com.orbvpn.api.service.payment.PaymentService;
import com.stripe.exception.StripeException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PaymentMutation {

  private final PaymentService paymentService;

  @MutationMapping
  public StripePaymentResponse stripeCreatePayment(
      @Argument PaymentCategory category,
      @Argument @Valid @Min(1) int groupId, 
      @Argument Integer moreLoginCount, // Changed from primitive int to Integer
      @Argument boolean renew,
      @Argument @Valid @NotBlank String paymentMethodId) throws StripeException {
      return paymentService.stripeCreatePayment(category, groupId, 
          moreLoginCount != null ? moreLoginCount : 0, // Provide default value
          renew, paymentMethodId);
  }

  @MutationMapping
  public CoinPaymentResponse coinpaymentCreatePayment(
      @Argument PaymentCategory category,
      @Argument @Valid @Min(1) int groupId,
      @Argument @Valid @Min(0) int moreLoginCount,
      @Argument @Valid @NotBlank String coin) throws Exception {
    log.info("Creating CoinPayment - category: {}, coin: {}", category, coin);
    return paymentService.coinpaymentCreatePayment(category, groupId, moreLoginCount, coin);
  }

  @MutationMapping
  public AddressResponse coinpaymentCreatePayment2(
      @Argument PaymentCategory category,
      @Argument @Valid @Min(1) int groupId,
      @Argument @Valid @Min(0) int moreLoginCount,
      @Argument @Valid @NotBlank String coin) throws IOException {
    log.info("Creating CoinPayment v2 - category: {}, coin: {}", category, coin);
    return paymentService.coinpaymentCreatePaymentV2(category, groupId, moreLoginCount, coin);
  }

  @MutationMapping
  public NowPaymentResponse nowPaymentCreatePayment(
      @Argument PaymentCategory category,
      @Argument @Valid @Min(1) int groupId,
      @Argument @Valid @Min(0) int moreLoginCount,
      @Argument @Valid @NotBlank String payCurrency) {
    log.info("Creating NOWPayment - category: {}, payCurrency: {}", category, payCurrency);
    return paymentService.nowPaymentCreatePayment(category, groupId, moreLoginCount, payCurrency);
  }

  @MutationMapping
  public PaypalCreatePaymentResponse paypalCreatePayment(
      @Argument PaymentCategory category,
      @Argument @Valid @Min(1) int groupId,
      @Argument @Valid @Min(0) int moreLoginCount) throws Exception {
    log.info("Creating PayPal payment - category: {}, groupId: {}", category, groupId);
    return paymentService.paypalCreatePayment(category, groupId, moreLoginCount);
  }

  @MutationMapping
  public PaypalApprovePaymentResponse paypalApprovePayment(
      @Argument @Valid @NotBlank String orderId) {
    log.info("Approving PayPal payment for order: {}", orderId);
    return paymentService.paypalApprovePayment(orderId);
  }

  @MutationMapping
  public ParspalCreatePaymentResponse parspalCreatePayment(
      @Argument PaymentCategory category,
      @Argument @Valid @Min(1) int groupId,
      @Argument @Valid @Min(0) int moreLoginCount) {
    log.info("Creating Parspal payment - category: {}, groupId: {}", category, groupId);
    return paymentService.parspalCreatePayment(category, groupId, moreLoginCount);
  }

  @MutationMapping
  public boolean parspalApprovePayment(
      @Argument @Valid @NotBlank String payment_id,
      @Argument @Valid @NotBlank String receipt_number) {
    log.info("Approving Parspal payment: {}", payment_id);
    return paymentService.parspalApprovePayment(payment_id, receipt_number);
  }

  @MutationMapping
  public boolean appleCreatePayment(
      @Argument @Valid @NotBlank String receipt) {
    log.info("Creating Apple payment");
    return paymentService.appleCreatePayment(receipt);
  }
}