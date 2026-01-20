package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MigrationResult {
    private int totalUsers;
    private int missingUuids;
    private int invalidUuids;
    private int fixedUuids;
}
