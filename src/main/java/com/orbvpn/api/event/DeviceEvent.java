package com.orbvpn.api.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.time.LocalDateTime;

@Getter
public class DeviceEvent extends ApplicationEvent {
    private final String deviceId;
    private final Long userId;
    private final String deviceType;
    private final String eventType;
    private final String ipAddress;
    private final String location;
    private final LocalDateTime eventDateTime;

    public DeviceEvent(Object source, String deviceId, Long userId, String deviceType,
            String eventType, String ipAddress, String location) {
        super(source);
        this.deviceId = deviceId;
        this.userId = userId;
        this.deviceType = deviceType;
        this.eventType = eventType;
        this.ipAddress = ipAddress;
        this.location = location;
        this.eventDateTime = LocalDateTime.now();
    }
}
