package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrbMeshDeviceCertificate {
    private String certificatePem;
    private String privateKeyPem;
    private String caCertificatePem;
    private String serialNumber;
    private String validFrom;
    private String validUntil;
}
