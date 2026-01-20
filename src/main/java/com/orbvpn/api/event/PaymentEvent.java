package com.orbvpn.api.event;

import com.orbvpn.api.domain.entity.Payment;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PaymentEvent extends ApplicationEvent {
    private final Payment payment;
    private final String eventType;

    public PaymentEvent(Object source, Payment payment, String eventType) {
        super(source);
        this.payment = payment;
        this.eventType = eventType;
    }
}
