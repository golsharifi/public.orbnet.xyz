package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class UserStatsByCountryView {
    private String continent;
    private int count;
    private List<CountryStatsView> countries;
}
