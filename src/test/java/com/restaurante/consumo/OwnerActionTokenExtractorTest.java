package com.restaurante.consumo;

import com.restaurante.consumo.participante.web.OwnerActionTokenExtractor;
import com.restaurante.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prompt 41.5 — Testes do OwnerActionTokenExtractor.
 */
@DisplayName("OwnerActionTokenExtractor — Prompt 41.5")
class OwnerActionTokenExtractorTest {

    OwnerActionTokenExtractor extractor;
    HttpServletRequest request;

    @BeforeEach
    void setUp() {
        extractor = new OwnerActionTokenExtractor();
        request   = mock(HttpServletRequest.class);
    }

    @Test
    @DisplayName("extrai do header X-Owner-Action-Token")
    void extrai_do_header() {
        when(request.getHeader("X-Owner-Action-Token")).thenReturn("header-token");
        assertThat(extractor.extractRequired(request, "body-token")).isEqualTo("header-token");
    }

    @Test
    @DisplayName("usa body como fallback se header ausente")
    void fallback_para_body() {
        when(request.getHeader("X-Owner-Action-Token")).thenReturn(null);
        assertThat(extractor.extractRequired(request, "body-token")).isEqualTo("body-token");
    }

    @Test
    @DisplayName("header tem prioridade sobre body")
    void header_tem_prioridade() {
        when(request.getHeader("X-Owner-Action-Token")).thenReturn("header-wins");
        assertThat(extractor.extractRequired(request, "body-loses")).isEqualTo("header-wins");
    }

    @Test
    @DisplayName("token blank lança OWNER_ACTION_TOKEN_REQUIRED")
    void token_blank_lanca_required() {
        when(request.getHeader("X-Owner-Action-Token")).thenReturn("   ");
        assertThatThrownBy(() -> extractor.extractRequired(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OWNER_ACTION_TOKEN_REQUIRED");
    }

    @Test
    @DisplayName("ambos ausentes lançam OWNER_ACTION_TOKEN_REQUIRED")
    void ambos_ausentes_lanca_required() {
        when(request.getHeader("X-Owner-Action-Token")).thenReturn(null);
        assertThatThrownBy(() -> extractor.extractRequired(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OWNER_ACTION_TOKEN_REQUIRED");
    }

    @Test
    @DisplayName("extract com required=false retorna null se ausente")
    void extract_optional_retorna_null() {
        when(request.getHeader("X-Owner-Action-Token")).thenReturn(null);
        assertThat(extractor.extract(request, null, false)).isNull();
    }

    @Test
    @DisplayName("query param ownerActionToken lança QUERY_PARAM_NOT_ALLOWED")
    void query_param_lanca_excecao() {
        when(request.getParameter("ownerActionToken")).thenReturn("leaked-token");
        assertThatThrownBy(() -> extractor.assertNotInQueryParam(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OWNER_ACTION_TOKEN_QUERY_PARAM_NOT_ALLOWED");
    }

    @Test
    @DisplayName("query param ausente não lança exceção")
    void query_param_ausente_ok() {
        when(request.getParameter("ownerActionToken")).thenReturn(null);
        when(request.getParameter("owner_action_token")).thenReturn(null);
        // Não deve lançar
        extractor.assertNotInQueryParam(request);
    }
}
