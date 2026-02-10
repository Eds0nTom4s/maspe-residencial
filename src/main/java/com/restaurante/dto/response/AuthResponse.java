package com.restaurante.dto.response;

import com.restaurante.model.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    
    @Builder.Default
    private String tokenType = "Bearer";
    
    private Long expiresIn;
    private String username;
    private Set<Role> roles;
}
