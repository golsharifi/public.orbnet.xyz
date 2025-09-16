package com.orbvpn.api.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class WhatsAppQRCodeEvent extends ApplicationEvent {
    private final String qrCode;

    public WhatsAppQRCodeEvent(Object source, String qrCode) {
        super(source);
        this.qrCode = qrCode;
    }
}