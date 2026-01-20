package com.orbvpn.api.domain.enums;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * Port forwarding addon pack types with their pricing.
 */
@Getter
public enum PortForwardAddonType {
    BASIC(3, new BigDecimal("1.99"), new BigDecimal("19.99"), "Basic"),
    STANDARD(5, new BigDecimal("2.99"), new BigDecimal("29.99"), "Standard"),
    ADVANCED(10, new BigDecimal("4.99"), new BigDecimal("49.99"), "Advanced"),
    POWER(25, new BigDecimal("9.99"), new BigDecimal("99.99"), "Power");

    private final int extraPorts;
    private final BigDecimal priceMonthly;
    private final BigDecimal priceYearly;
    private final String displayName;

    PortForwardAddonType(int extraPorts, BigDecimal priceMonthly,
                         BigDecimal priceYearly, String displayName) {
        this.extraPorts = extraPorts;
        this.priceMonthly = priceMonthly;
        this.priceYearly = priceYearly;
        this.displayName = displayName;
    }

    /**
     * Alias for getExtraPorts() for convenience
     */
    public int getPorts() {
        return extraPorts;
    }
}
