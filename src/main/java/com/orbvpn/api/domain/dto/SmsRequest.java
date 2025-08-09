package com.orbvpn.api.domain.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@Setter
@Getter
@RequiredArgsConstructor
public class SmsRequest {
    private final String phoneNumber;
    private final String message;
}
