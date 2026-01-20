package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CountryStatsView {
    private String country;
    private int count;
}
