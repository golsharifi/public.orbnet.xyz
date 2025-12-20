// File: com/orbvpn/api/config/sms/AfeConfig.java
package com.orbvpn.api.config.sms;

import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "afe")
@Validated
@Data
public class AfeConfig {
    @NotBlank(message = "AFE.ir Username is required")
    private String username;

    @NotBlank(message = "AFE.ir Password is required")
    private String password;

    @NotBlank(message = "AFE.ir Number is required")
    private String number;
}