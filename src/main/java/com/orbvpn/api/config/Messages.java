package com.orbvpn.api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class Messages {

    private static final ResourceBundleMessageSource source;

    static {
        source = new ResourceBundleMessageSource();
        source.setBasename("messages/messages");
        source.setUseCodeAsDefaultMessage(true);
    }

    public static String getMessage(String code) {
        return source.getMessage(code, null, Locale.ENGLISH);
    }

}
