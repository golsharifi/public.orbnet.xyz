package com.orbvpn.api.service.payment;

import com.orbvpn.api.config.PayPalClient;
import com.orbvpn.api.domain.dto.PaypalApprovePaymentResponse;
import com.orbvpn.api.domain.dto.PaypalCreatePaymentResponse;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.exception.PaymentException;
import com.orbvpn.api.repository.PaymentRepository;
import com.paypal.http.HttpResponse;
import com.paypal.orders.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaypalService {

  private final PayPalClient paypalClient;
  private final PaymentRepository paymentRepository;

  /**
   * Creates a PayPal order for the given payment.
   * Optimized to not hold database connection during external API call.
   *
   * @param payment The payment entity to create an order for
   * @return PayPal response containing the order ID
   * @throws PaymentException if order creation fails
   */
  public PaypalCreatePaymentResponse createPayment(Payment payment) {
    log.info("Creating PayPal order for payment ID: {}, amount: {}",
            payment.getId(), payment.getPrice());

    // Validate payment
    if (payment.getPrice() == null || payment.getPrice().doubleValue() <= 0) {
      throw new PaymentException("Invalid payment amount");
    }

    // Check if payment already has an order ID (prevent duplicates)
    if (payment.getPaymentId() != null && !payment.getPaymentId().isEmpty()) {
      log.warn("Payment {} already has PayPal order ID: {}", payment.getId(), payment.getPaymentId());
      PaypalCreatePaymentResponse response = new PaypalCreatePaymentResponse();
      response.setOrderId(payment.getPaymentId());
      return response;
    }

    // Build request before any external call
    OrdersCreateRequest request = new OrdersCreateRequest();
    request.prefer("return=representation");
    request.requestBody(buildRequestBody(payment));

    try {
      // External API call - NOT inside a transaction (no DB connection held)
      HttpResponse<Order> response = paypalClient.client().execute(request);

      PaypalCreatePaymentResponse paypalResponse = new PaypalCreatePaymentResponse();

      if (response.statusCode() == 201 || response.statusCode() == 200) {
        Order order = response.result();
        String orderId = order.id();

        log.info("PayPal order created successfully - Order ID: {}, Status: {}",
                orderId, order.status());

        paypalResponse.setOrderId(orderId);

        // Update payment in a short transaction
        updatePaymentSuccess(payment.getId(), orderId);

        return paypalResponse;

      } else {
        String errorMsg = String.format("PayPal returned unexpected status: %d", response.statusCode());
        log.error(errorMsg);
        updatePaymentFailure(payment.getId(), errorMsg);
        throw new PaymentException(errorMsg);
      }

    } catch (IOException e) {
      String errorMsg = "Failed to communicate with PayPal: " + e.getMessage();
      log.error(errorMsg, e);
      updatePaymentFailure(payment.getId(), errorMsg);
      throw new PaymentException(errorMsg, e);
    }
  }

  @Transactional
  protected void updatePaymentSuccess(Integer paymentId, String orderId) {
    paymentRepository.findById(paymentId).ifPresent(p -> {
      p.setPaymentId(orderId);
      p.setStatus(PaymentStatus.PENDING);
      paymentRepository.save(p);
    });
  }

  @Transactional
  protected void updatePaymentFailure(Integer paymentId, String errorMsg) {
    paymentRepository.findById(paymentId).ifPresent(p -> {
      p.setStatus(PaymentStatus.FAILED);
      p.setErrorMessage(errorMsg);
      paymentRepository.save(p);
    });
  }

  /**
   * Captures/approves a PayPal order after buyer approval.
   *
   * @param orderId The PayPal order ID to capture
   * @return Response indicating success or failure
   */
  @Transactional
  public PaypalApprovePaymentResponse approvePayment(String orderId) {
    log.info("Capturing PayPal order: {}", orderId);

    if (orderId == null || orderId.isEmpty()) {
      throw new PaymentException("Order ID is required");
    }

    PaypalApprovePaymentResponse response = new PaypalApprovePaymentResponse();

    // Find the payment with pessimistic lock to prevent race conditions
    Payment payment = paymentRepository.findByGatewayAndPaymentIdWithLock(GatewayName.PAYPAL, orderId)
            .orElseThrow(() -> new PaymentException("Payment not found for PayPal order: " + orderId));

    // Check if already processed (idempotency)
    if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
      log.info("PayPal payment {} already succeeded, skipping capture", orderId);
      response.setSuccess(true);
      return response;
    }

    if (payment.getStatus() == PaymentStatus.FAILED) {
      log.warn("Attempting to capture failed payment: {}", orderId);
      response.setSuccess(false);
      response.setErrorMessage("Payment has already failed");
      return response;
    }

    try {
      OrdersCaptureRequest request = new OrdersCaptureRequest(orderId);
      request.requestBody(new OrderRequest());

      HttpResponse<Order> paypalResponse = paypalClient.client().execute(request);
      Order capturedOrder = paypalResponse.result();

      if (paypalResponse.statusCode() == HttpStatus.SC_OK ||
          paypalResponse.statusCode() == HttpStatus.SC_CREATED) {

        log.info("PayPal order {} captured successfully - Status: {}",
                orderId, capturedOrder.status());

        // Verify capture status
        if ("COMPLETED".equals(capturedOrder.status())) {
          response.setSuccess(true);
          payment.setStatus(PaymentStatus.SUCCEEDED);

          // Store capture details in metadata if available
          if (capturedOrder.purchaseUnits() != null && !capturedOrder.purchaseUnits().isEmpty()) {
            PurchaseUnit pu = capturedOrder.purchaseUnits().get(0);
            if (pu.payments() != null && pu.payments().captures() != null
                && !pu.payments().captures().isEmpty()) {
              Capture capture = pu.payments().captures().get(0);
              log.info("Capture ID: {}, Amount: {} {}",
                      capture.id(),
                      capture.amount().value(),
                      capture.amount().currencyCode());
            }
          }
        } else {
          log.warn("PayPal order {} has unexpected status: {}", orderId, capturedOrder.status());
          response.setSuccess(false);
          response.setErrorMessage("Unexpected order status: " + capturedOrder.status());
          payment.setStatus(PaymentStatus.PROCESSING);
        }

      } else {
        String errorMsg = String.format("PayPal capture failed with status: %d", paypalResponse.statusCode());
        log.error(errorMsg);
        response.setSuccess(false);
        response.setErrorMessage(errorMsg);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setErrorMessage(errorMsg);
      }

    } catch (IOException e) {
      String errorMsg = "Failed to capture PayPal order: " + e.getMessage();
      log.error(errorMsg, e);
      response.setSuccess(false);
      response.setErrorMessage(errorMsg);
      payment.setStatus(PaymentStatus.FAILED);
      payment.setErrorMessage(errorMsg);
    } catch (Exception e) {
      String errorMsg = "Unexpected error capturing PayPal order: " + e.getMessage();
      log.error(errorMsg, e);
      response.setSuccess(false);
      response.setErrorMessage(errorMsg);
      payment.setStatus(PaymentStatus.FAILED);
      payment.setErrorMessage(errorMsg);
    }

    paymentRepository.save(payment);
    return response;
  }

  /**
   * Builds the PayPal OrderRequest with proper details.
   */
  private OrderRequest buildRequestBody(Payment payment) {
    OrderRequest orderRequest = new OrderRequest();
    orderRequest.checkoutPaymentIntent("CAPTURE");

    // Build purchase unit with amount
    List<PurchaseUnitRequest> purchaseUnitRequests = new ArrayList<>();
    PurchaseUnitRequest purchaseUnitRequest = new PurchaseUnitRequest()
            .referenceId(payment.getId().toString())
            .description(buildDescription(payment))
            .amountWithBreakdown(new AmountWithBreakdown()
                    .currencyCode("USD")
                    .value(payment.getPrice().setScale(2, java.math.RoundingMode.HALF_UP).toString()));

    purchaseUnitRequests.add(purchaseUnitRequest);
    orderRequest.purchaseUnits(purchaseUnitRequests);

    // Set application context for better UX
    ApplicationContext applicationContext = new ApplicationContext()
            .brandName("OrbVPN")
            .landingPage("BILLING")
            .userAction("PAY_NOW")
            .shippingPreference("NO_SHIPPING");
    orderRequest.applicationContext(applicationContext);

    return orderRequest;
  }

  /**
   * Builds a description for the PayPal order.
   */
  private String buildDescription(Payment payment) {
    if (payment.getCategory() != null) {
      switch (payment.getCategory()) {
        case GROUP:
          return "OrbVPN Subscription";
        case MORE_LOGIN:
          return "OrbVPN Extra Logins";
        default:
          return "OrbVPN Payment";
      }
    }
    return "OrbVPN Payment";
  }
}
