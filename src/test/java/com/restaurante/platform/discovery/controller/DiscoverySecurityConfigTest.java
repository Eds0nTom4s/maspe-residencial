package com.restaurante.platform.discovery.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurante.config.CorsConfig;
import com.restaurante.config.SecurityConfig;
import com.restaurante.platform.discovery.domain.DiscoveryResult;
import com.restaurante.platform.discovery.dto.DiscoveryHomeResponse;
import com.restaurante.platform.discovery.dto.HomeDiscoveryRequest;
import com.restaurante.platform.discovery.dto.MerchantSearchResponse;
import com.restaurante.platform.discovery.dto.SearchDiscoveryRequest;
import com.restaurante.platform.discovery.service.DiscoveryService;
import com.restaurante.platform.discovery.validation.DiscoveryHttpParameterValidator;
import com.restaurante.security.CustomUserDetailsService;
import com.restaurante.security.JwtAuthenticationFactory;
import com.restaurante.security.JwtSecurityExceptionHandlers.JwtAccessDeniedHandler;
import com.restaurante.security.JwtSecurityExceptionHandlers.JwtAuthenticationEntryPoint;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.security.JwtUserStatusValidator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = DiscoveryController.class,
        properties = "cors.allowed-origins=https://frontend.example.test")
@Import({
    SecurityConfig.class,
    CorsConfig.class,
    JwtAuthenticationEntryPoint.class,
    JwtAccessDeniedHandler.class,
    DiscoveryHttpParameterValidator.class
})
@ActiveProfiles("discovery-security")
class DiscoverySecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DiscoveryService service;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private JwtAuthenticationFactory authenticationFactory;

    @MockBean
    private JwtUserStatusValidator userStatusValidator;

    @MockBean
    private AuthenticationProvider authenticationProvider;

    @BeforeEach
    void setUp() {
        var emptySection = new com.restaurante.platform.discovery.dto.DiscoveryDtos.MerchantSectionDto(
                List.of(), false);
        when(service.home(any(HomeDiscoveryRequest.class)))
                .thenReturn(new DiscoveryResult.Empty<>(new DiscoveryHomeResponse(
                        List.of(), emptySection, emptySection, emptySection)));
        when(service.search(any(SearchDiscoveryRequest.class)))
                .thenReturn(new DiscoveryResult.Empty<>(new MerchantSearchResponse(
                        List.of(), List.of(), 0, 20, 0, false)));
    }

    @Test
    void exactPublicGetsAcceptAbsentValidInvalidExpiredAndEmptyBearerTokens() throws Exception {
        mvc.perform(get("/v1/discovery/home")).andExpect(status().isOk());
        mvc.perform(get("/v1/discovery/home").header("Authorization", "Bearer valid.jwt"))
                .andExpect(status().isOk());
        mvc.perform(get("/v1/discovery/home").header("Authorization", "Bearer invalid.jwt"))
                .andExpect(status().isOk());
        mvc.perform(get("/v1/discovery/search").header("Authorization", "Bearer expired.jwt"))
                .andExpect(status().isOk());
        mvc.perform(get("/v1/discovery/search").header("Authorization", "Bearer "))
                .andExpect(status().isOk());
    }

    @Test
    void writeMethodsOnDiscoveryRemainProtectedByTheRealSecurityChain() throws Exception {
        mvc.perform(post("/v1/discovery/search")).andExpect(status().isUnauthorized());
        mvc.perform(put("/v1/discovery/home")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/v1/discovery/home")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/v1/discovery/home")).andExpect(status().isUnauthorized());
    }

    @Test
    void unrelatedRouteRemainsPrivateAndCorsPreflightUsesConfiguredOrigin() throws Exception {
        mvc.perform(get("/v1/discovery/internal"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/discovery/internal")
                        .header("Authorization", "Bearer invalid.jwt"))
                .andExpect(status().isUnauthorized());

        mvc.perform(options("/v1/discovery/home")
                        .header("Origin", "https://frontend.example.test")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin", "https://frontend.example.test"));
    }
}
