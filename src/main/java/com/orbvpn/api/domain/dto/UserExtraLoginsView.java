package com.orbvpn.api.domain.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserExtraLoginsView {
    private Long id;
    private String planName;
    private int loginCount;
    private LocalDateTime startDate;
    private LocalDateTime expiryDate;
    private boolean active;
    private boolean subscription;
    private String giftedByEmail;
}