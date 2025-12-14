package com.orbvpn.api.config.logging;

/**
 */
public class OrbRollingFileAppender extends InternalRollingFileAppender {
    @Override
    public String getAppName() {
        return "ORB";
    }
}
