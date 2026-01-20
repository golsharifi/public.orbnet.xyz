package com.orbvpn.api.domain.payload.NowPayment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

/**
 * Response from NOWPayments API for available currencies.
 * GET https://api.nowpayments.io/v1/currencies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CurrenciesResponse {

    /**
     * List of available cryptocurrency codes
     */
    private List<String> currencies;
}
