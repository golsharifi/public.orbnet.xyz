package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WithdrawResult {
    private boolean success;
    private String transactionHash;
    private String message;

}
