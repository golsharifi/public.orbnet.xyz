package com.orbvpn.api.domain.enums;

/**
 * Type of discovered network device.
 */
public enum DeviceType {
    UNKNOWN,        // Unable to identify
    ROUTER,         // Network router/gateway
    COMPUTER,       // Desktop/Laptop computer
    PHONE,          // Smartphone
    TABLET,         // Tablet device
    TV,             // Smart TV
    IOT,            // Internet of Things device
    PRINTER,        // Network printer
    CAMERA,         // Security/IP camera
    NAS,            // Network Attached Storage
    GAME_CONSOLE,   // Gaming console
    SMART_SPEAKER,  // Smart speaker (Alexa, Google Home)
    STREAMING_BOX,  // Media streaming device
    SERVER,         // Server machine
    NETWORK_DEVICE, // Switch, AP, etc.
    WEARABLE        // Smartwatch, fitness tracker
}
