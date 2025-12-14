package com.orbvpn.api.domain.dto.partner;

import lombok.Data;

@Data
public class CreatePartnerInput {
    private String partnerName;
    private String contactEmail;
    private String contactPhone;
    private String companyName;
    private String countryCode;
}
