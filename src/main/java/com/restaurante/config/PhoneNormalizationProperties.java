package com.restaurante.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "consuma.otp.phone")
public class PhoneNormalizationProperties {

    private String defaultCountryCode = "+244";
    private String countryMode = "ANGOLA";

    public String getDefaultCountryCode() { return defaultCountryCode; }
    public void setDefaultCountryCode(String defaultCountryCode) { this.defaultCountryCode = defaultCountryCode; }
    public String getCountryMode() { return countryMode; }
    public void setCountryMode(String countryMode) { this.countryMode = countryMode; }
}

