package com.orbvpn.api.domain.dto;

import lombok.Data;
import java.util.List;

@Data
public class OrbMeshDeviceBatchInput {
    private Integer count;
    private String deviceModel;
    private String manufacturingBatch;
    private List<String> hardwareFingerprints;
}
