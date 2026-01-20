package com.orbvpn.api.domain.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BulkSubscription {
    private Integer groupId;
    private Integer duration;
    private Integer multiLoginCount;
    private LocalDateTime expiresAt;

}
