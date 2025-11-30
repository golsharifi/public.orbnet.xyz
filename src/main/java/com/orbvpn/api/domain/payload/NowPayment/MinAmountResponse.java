package com.orbvpn.api.domain.payload.NowPayment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

/**
 * Response from NOWPayments API for minimum payment amount.
 * GET https://api.nowpayments.io/v1/min-amount?currency_from=btc&currency_to=usd
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MinAmountResponse {

    @JsonProperty("currency_from")
    private String currencyFrom;

    @JsonProperty("currency_to")
    private String currencyTo;

    @JsonProperty("min_amount")
    private BigDecimal minAmount;

    @JsonProperty("fiat_equivalent")
    private BigDecimal fiatEquivalent;
}
