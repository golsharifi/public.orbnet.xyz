package com.orbvpn.api.domain.payload.NowPayment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

/**
 * Response from NOWPayments API for estimated price.
 * GET https://api.nowpayments.io/v1/estimate?amount=100&currency_from=usd&currency_to=btc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EstimateResponse {

    @JsonProperty("currency_from")
    private String currencyFrom;

    @JsonProperty("currency_to")
    private String currencyTo;

    @JsonProperty("amount_from")
    private BigDecimal amountFrom;

    @JsonProperty("estimated_amount")
    private BigDecimal estimatedAmount;
}
