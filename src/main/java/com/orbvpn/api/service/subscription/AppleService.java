package com.orbvpn.api.service.subscription;

import com.orbvpn.api.domain.dto.AppStoreVerifyReceiptRequest;
import com.orbvpn.api.domain.dto.AppStoreVerifyReceiptResponse;
import com.orbvpn.api.domain.dto.AppStoreVerifyReceiptResponse.LatestReceiptInfo;
import com.orbvpn.api.domain.dto.AppleSubscriptionData;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.config.AppleConfiguration;
import com.orbvpn.api.exception.SubscriptionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@Slf4j
public class AppleService {
  @Value("${app-store.secret}")
  private String SECRET;

  private final ProductGroupMapper productGroupMapper;
  private final AppleConfiguration appleConfig;
  private final RestTemplate restTemplate = new RestTemplate();

  public AppleService(
      ProductGroupMapper productGroupMapper,
      AppleConfiguration appleConfiguration) {
    this.productGroupMapper = productGroupMapper;
    this.appleConfig = appleConfiguration;
    log.info("Initialized AppleService");
  }

  @Transactional(readOnly = true)
  public AppleSubscriptionData getSubscriptionData(String receipt, String deviceId, User user) {
    log.info("Verifying receipt with Apple for user: {}", user.getId());

    if (receipt == null || receipt.trim().isEmpty()) {
      throw new IllegalArgumentException("Receipt data cannot be null or empty");
    }

    try {
      AppStoreVerifyReceiptResponse response = verifyReceiptWithApple(receipt);

      if (response == null || response.getStatus() != 0) {
        String errorMsg = getAppleErrorMessage(response != null ? response.getStatus() : -1);
        log.error("Apple verification failed: {}", errorMsg);
        throw new SubscriptionException("Apple verification failed: " + errorMsg);
      }

      if (response.getLatestReceiptInfo() == null || response.getLatestReceiptInfo().isEmpty()) {
        log.error("No receipt info available in Apple's response");
        throw new SubscriptionException("Invalid response from Apple: No receipt info available");
      }

      LatestReceiptInfo latestReceiptInfo = getLatestValidReceipt(response);

      if (latestReceiptInfo == null || latestReceiptInfo.getProductId() == null
          || latestReceiptInfo.getExpiresDateMs() == null) {
        log.error("Invalid receipt info: Missing required fields");
        throw new SubscriptionException("Invalid receipt info");
      }

      int groupId = mapProductIdToGroupId(latestReceiptInfo.getProductId());
      LocalDateTime expiresAt = parseExpirationDate(latestReceiptInfo);
      boolean isTrialPeriod = "true".equalsIgnoreCase(latestReceiptInfo.getIsTrialPeriod());
      return buildSubscriptionData(groupId, expiresAt, receipt, latestReceiptInfo, isTrialPeriod);
    } catch (SubscriptionException se) {
      throw se;
    } catch (Exception e) {
      log.error("Error processing Apple subscription for user: {} - Error: {}", user.getId(), e.getMessage(), e);
      throw new SubscriptionException("Failed to process Apple subscription: " + e.getMessage());
    }
  }

  private AppStoreVerifyReceiptResponse verifyReceiptWithApple(String receipt) {
    try {
      AppStoreVerifyReceiptRequest request = new AppStoreVerifyReceiptRequest();
      request.setReceiptData(receipt);
      request.setPassword(SECRET);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<AppStoreVerifyReceiptRequest> httpRequest = new HttpEntity<>(request, headers);

      // Use production URL first
      String verifyUrl = appleConfig.getVerification().getUrl();
      AppStoreVerifyReceiptResponse response = restTemplate.postForObject(
          verifyUrl, httpRequest, AppStoreVerifyReceiptResponse.class);

      // If we get status 21007, retry with sandbox URL
      if (response != null && response.getStatus() == 21007) {
        log.info("Receipt is from sandbox, retrying with sandbox URL");
        verifyUrl = appleConfig.getVerification().getSandboxUrl();
        response = restTemplate.postForObject(
            verifyUrl, httpRequest, AppStoreVerifyReceiptResponse.class);
      }

      return response;
    } catch (Exception e) {
      log.error("Failed to verify receipt with Apple: {}", e.getMessage());
      throw new SubscriptionException("Failed to verify receipt with Apple", e);
    }
  }

  private String getAppleErrorMessage(int statusCode) {
    return switch (statusCode) {
      case 0 -> null; // Success
      case 21000 -> "The App Store could not read the JSON object you provided.";
      case 21002 -> "The data in the receipt-data property was malformed.";
      case 21003 -> "The receipt could not be authenticated.";
      case 21004 -> "The shared secret you provided does not match the shared secret on file for your account.";
      case 21005 -> "The receipt server is not currently available.";
      case 21006 -> "This receipt is valid but the subscription has expired.";
      case 21007 ->
        "This receipt is from the test environment, but it was sent to the production environment for verification.";
      case 21008 ->
        "This receipt is from the production environment, but it was sent to the test environment for verification.";
      default -> "Unknown error occurred with status: " + statusCode;
    };
  }

  private LatestReceiptInfo getLatestValidReceipt(AppStoreVerifyReceiptResponse response) {
    return response.getLatestReceiptInfo().stream()
        .filter(info -> info.getCancellationDate() == null || info.getCancellationDate().isEmpty())
        .max((info1, info2) -> Long.compare(
            Long.parseLong(info1.getExpiresDateMs()),
            Long.parseLong(info2.getExpiresDateMs())))
        .orElse(null);
  }

  private LocalDateTime parseExpirationDate(LatestReceiptInfo receiptInfo) {
    try {
      long expirationMs = Long.parseLong(receiptInfo.getExpiresDateMs());
      return Instant.ofEpochMilli(expirationMs)
          .atZone(ZoneId.systemDefault())
          .toLocalDateTime();
    } catch (NumberFormatException e) {
      log.error("Invalid expiration date format: {}", receiptInfo.getExpiresDateMs());
      throw new IllegalArgumentException("Invalid expiration date format");
    }
  }

  private AppleSubscriptionData buildSubscriptionData(int groupId, LocalDateTime expiresAt,
      String receipt, LatestReceiptInfo receiptInfo, boolean isTrialPeriod) {

    AppleSubscriptionData data = new AppleSubscriptionData();
    data.setGroupId(groupId);
    data.setExpiresAt(expiresAt);
    data.setReceipt(receipt);
    data.setOriginalTransactionId(receiptInfo.getOriginalTransactionId());
    data.setTransactionId(receiptInfo.getTransactionId());
    data.setIsTrialPeriod(isTrialPeriod);

    // Get price from configuration
    AppleConfiguration.AppStoreProduct product = appleConfig.getProducts().get(receiptInfo.getProductId());
    if (product != null) {
      data.setPrice(product.getPrice());
      data.setCurrency(product.getCurrency());
    }

    // If it's a trial, set trial end date
    if (isTrialPeriod) {
      data.setTrialEndDate(expiresAt);
    }

    return data;
  }

  private int mapProductIdToGroupId(String productId) {
    try {
      return productGroupMapper.mapProductIdToGroupId(productId);
    } catch (IllegalArgumentException e) {
      throw new SubscriptionException("Unknown product SKU: " + productId, e);
    }
  }
}