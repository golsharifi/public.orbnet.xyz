package com.orbvpn.api.service.notification.sms;

public interface SmsProvider {
    void sendSms(String phoneNumber, String message);

    boolean isApplicable(String phoneNumber);
}