package com.orbvpn.api.domain.dto;

import lombok.Data;
import java.util.List;

@Data
public class OrbMeshDeviceBatchResult {
    private Boolean success;
    private String message;
    private Integer count;
    private String batchId;
    private List<OrbMeshDeviceIdentityCreationResult> devices;
}
