package com.restaurante.platform;

import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.main.web-application-type=servlet",
        "jwt.secret=0123456789ABCDEF0123456789ABCDEF",
        "jwt.expiration=3600000",
        "cors.allowed-origins=https://frontend.example.test"
})
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("it-postgres")
class CorsCanonicalHeadersIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;

    @Test
    void realSecurityFilterPreflightAllowsCanonicalHeadersForBothActivationCommands() throws Exception {
        assertPreflight("/platform/business-accounts/1/activate");
        assertPreflight("/platform/business-accounts/1/businesses/2/activate");
    }

    private void assertPreflight(String path) throws Exception {
        var response = mockMvc.perform(options(path)
                        .header("Origin", "https://frontend.example.test")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers",
                                "authorization,content-type,idempotency-key,x-correlation-id"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse();

        String allowed = response.getHeader("Access-Control-Allow-Headers");
        assertThat(allowed).isNotNull();
        String normalized = allowed.toLowerCase(Locale.ROOT);
        assertThat(normalized).contains("authorization", "content-type", "idempotency-key", "x-correlation-id");
    }
}
