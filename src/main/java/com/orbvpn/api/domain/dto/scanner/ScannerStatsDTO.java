package com.orbvpn.api.domain.dto.scanner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScannerStatsDTO {
    private Long totalScans;
    private Long scansToday;
    private Long scansThisWeek;
    private Double averageSecurityScore;
    private Map<String, Long> gradeDistribution;
}
