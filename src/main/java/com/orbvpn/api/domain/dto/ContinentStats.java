package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ContinentStats {
    private String continent;
    private int count;
    private List<String> countries;
}
