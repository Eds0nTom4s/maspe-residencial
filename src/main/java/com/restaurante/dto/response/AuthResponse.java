package com.restaurante.dto.response;

import com.restaurante.model.enums.Role;


import java.util.Set;

public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    
    private String tokenType = "Bearer";
    
    private Long expiresIn;
    private String username;
    private Set<Role> roles;

    public AuthResponse() {}

    public AuthResponse(String accessToken, String refreshToken, String tokenType, Long expiresIn, String username, Set<Role> roles) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType != null ? tokenType : "Bearer";
        this.expiresIn = expiresIn;
        this.username = username;
        this.roles = roles;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public Long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(Long expiresIn) { this.expiresIn = expiresIn; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }

    public static AuthResponseBuilder builder() {
        return new AuthResponseBuilder();
    }

    public static class AuthResponseBuilder {
        private String accessToken;
        private String refreshToken;
        private String tokenType = "Bearer";
        private Long expiresIn;
        private String username;
        private Set<Role> roles;

        AuthResponseBuilder() {}

        public AuthResponseBuilder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public AuthResponseBuilder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public AuthResponseBuilder tokenType(String tokenType) {
            this.tokenType = tokenType;
            return this;
        }

        public AuthResponseBuilder expiresIn(Long expiresIn) {
            this.expiresIn = expiresIn;
            return this;
        }

        public AuthResponseBuilder username(String username) {
            this.username = username;
            return this;
        }

        public AuthResponseBuilder roles(Set<Role> roles) {
            this.roles = roles;
            return this;
        }

        public AuthResponse build() {
            return new AuthResponse(this.accessToken, this.refreshToken, this.tokenType, this.expiresIn, this.username, this.roles);
        }
    }
}
