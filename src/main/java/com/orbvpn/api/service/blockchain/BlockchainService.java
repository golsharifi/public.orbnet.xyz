package com.orbvpn.api.service.blockchain;

import java.math.BigDecimal;

public interface BlockchainService {
    String transferTokens(String address, BigDecimal amount);

}
