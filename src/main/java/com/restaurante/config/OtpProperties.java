package com.restaurante.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "consuma.otp")
public class OtpProperties {

    private boolean enabled = true;
    private int length = 6;
    private int ttlMinutes = 5;
    private int maxAttempts = 5;
    private int resendCooldownSeconds = 60;
    private int maxResends = 3;
    private int maxActiveChallengesPerPhone = 1;
    private String hashPepper = "";
    private boolean mockEnabled = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }
    public int getTtlMinutes() { return ttlMinutes; }
    public void setTtlMinutes(int ttlMinutes) { this.ttlMinutes = ttlMinutes; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public int getResendCooldownSeconds() { return resendCooldownSeconds; }
    public void setResendCooldownSeconds(int resendCooldownSeconds) { this.resendCooldownSeconds = resendCooldownSeconds; }
    public int getMaxResends() { return maxResends; }
    public void setMaxResends(int maxResends) { this.maxResends = maxResends; }
    public int getMaxActiveChallengesPerPhone() { return maxActiveChallengesPerPhone; }
    public void setMaxActiveChallengesPerPhone(int maxActiveChallengesPerPhone) { this.maxActiveChallengesPerPhone = maxActiveChallengesPerPhone; }
    public String getHashPepper() { return hashPepper; }
    public void setHashPepper(String hashPepper) { this.hashPepper = hashPepper; }
    public boolean isMockEnabled() { return mockEnabled; }
    public void setMockEnabled(boolean mockEnabled) { this.mockEnabled = mockEnabled; }
}

