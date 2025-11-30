package com.orbvpn.api.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppStoreVerifyReceiptResponse {
  private String environment;

  // Add status field for the receipt verification response
  private int status;

  @JsonProperty("latest_receipt_info")
  private List<LatestReceiptInfo> latestReceiptInfo;

  @Getter
  @Setter
  public static class LatestReceiptInfo {
    @JsonProperty("product_id")
    private String productId;

    @JsonProperty("expires_date_ms")
    private String expiresDateMs;

    @JsonProperty("original_transaction_id")
    private String originalTransactionId;

    @JsonProperty("is_trial_period")
    private String isTrialPeriod;

    @JsonProperty("cancellation_date")
    private String cancellationDate;

    @JsonProperty("transaction_id")
    private String transactionId;

    private Long price;
    private String currency;

    // Add getters and setters
    public Long getPrice() {
      return price;
    }

    public void setPrice(Long price) {
      this.price = price;
    }

    public String getCurrency() {
      return currency;
    }

    public void setCurrency(String currency) {
      this.currency = currency;
    }

  }

}