package com.orbvpn.api.domain.enums;

public enum WebhookProviderType {
    SLACK("SLACK"),
    GOHIGHLEVEL("GOHIGHLEVEL"),
    ZAPIER("ZAPIER");

    private final String value;

    WebhookProviderType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static WebhookProviderType fromString(String text) {
        for (WebhookProviderType type : WebhookProviderType.values()) {
            if (type.value.equalsIgnoreCase(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No constant with text " + text + " found");
    }
}