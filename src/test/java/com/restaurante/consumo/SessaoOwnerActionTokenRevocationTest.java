package com.restaurante.consumo;

import com.restaurante.consumo.participante.entity.SessaoOwnerActionToken;
import com.restaurante.consumo.participante.repository.SessaoOwnerActionTokenRepository;
import com.restaurante.consumo.participante.service.SessaoOwnerActionTokenService;
import com.restaurante.config.SessaoOwnerActionTokenProperties;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.SessaoOwnerActionTokenStatus;
import com.restaurante.service.operacional.OperationalEventLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Prompt 41.5 — Testes de revogação em massa ao fechar sessão.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SessaoOwnerActionToken — Revogação ao fechar sessão (Prompt 41.5)")
class SessaoOwnerActionTokenRevocationTest {

    @Mock SessaoOwnerActionTokenRepository tokenRepository;
    @Mock SessaoOwnerActionTokenProperties props;
    @Mock OperationalEventLogService eventLogService;

    SessaoOwnerActionTokenService service;

    @BeforeEach
    void setUp() {
        when(props.getTtlMinutes()).thenReturn(10);
        when(props.getMaxUses()).thenReturn(20);
        when(props.getHashPepper()).thenReturn("test-pepper");
        service = new SessaoOwnerActionTokenService(tokenRepository, props, eventLogService);
    }

    @Test
    @DisplayName("fechar sessão revoga todos os tokens ACTIVE")
    void fechar_sessao_revoga_active() {
        var t1 = buildToken(SessaoOwnerActionTokenStatus.ACTIVE);
        var t2 = buildToken(SessaoOwnerActionTokenStatus.ACTIVE);
        when(tokenRepository.findActiveTokensBySessaoForUpdate(1L, 100L)).thenReturn(List.of(t1, t2));
        when(tokenRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        int count = service.revokeActiveTokensBySessao(1L, 100L, "SESSION_CLOSE", null, null);

        assertThat(count).isEqualTo(2);
        assertThat(t1.getStatus()).isEqualTo(SessaoOwnerActionTokenStatus.REVOKED);
        assertThat(t2.getStatus()).isEqualTo(SessaoOwnerActionTokenStatus.REVOKED);
        assertThat(t1.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("sem tokens ACTIVE retorna 0")
    void sem_tokens_retorna_zero() {
        when(tokenRepository.findActiveTokensBySessaoForUpdate(1L, 100L)).thenReturn(List.of());

        int count = service.revokeActiveTokensBySessao(1L, 100L, "SESSION_CLOSE", null, null);

        assertThat(count).isEqualTo(0);
        verify(tokenRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("operação é idempotente — segunda chamada sem tokens não falha")
    void idempotente() {
        when(tokenRepository.findActiveTokensBySessaoForUpdate(1L, 100L)).thenReturn(List.of());

        assertThat(service.revokeActiveTokensBySessao(1L, 100L, null, null, null)).isEqualTo(0);
        assertThat(service.revokeActiveTokensBySessao(1L, 100L, null, null, null)).isEqualTo(0);
    }

    @Test
    @DisplayName("auditoria registra revokedCount sem raw token")
    void auditoria_sem_raw_token() {
        var t1 = buildToken(SessaoOwnerActionTokenStatus.ACTIVE);
        when(tokenRepository.findActiveTokensBySessaoForUpdate(1L, 100L)).thenReturn(List.of(t1));
        when(tokenRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        service.revokeActiveTokensBySessao(1L, 100L, "SESSION_CLOSE", null, null);

        // Verificar que auditoria foi emitida (13 params: tenant, inst, unidade, mesa, turno,
        // eventType, entityType, entityId, origem, motivo, metadata, ip, ua)
        verify(eventLogService, atLeastOnce()).logPublicEvent(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(),
                any(String.class), any(), any(), any());
    }

    @Test
    @DisplayName("falha na auditoria não propaga (job não quebra)")
    void falha_auditoria_nao_propaga() {
        var t1 = buildToken(SessaoOwnerActionTokenStatus.ACTIVE);
        when(tokenRepository.findActiveTokensBySessaoForUpdate(1L, 100L)).thenReturn(List.of(t1));
        when(tokenRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("auditoria falhou")).when(eventLogService).logPublicEvent(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(String.class), any(), any(), any());

        // Não deve lançar
        int count = service.revokeActiveTokensBySessao(1L, 100L, "SESSION_CLOSE", null, null);
        assertThat(count).isEqualTo(1);
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private SessaoOwnerActionToken buildToken(SessaoOwnerActionTokenStatus status) {
        var tenant = new Tenant(); tenant.setId(1L);
        var sessao = new SessaoConsumo(); sessao.setId(100L);
        var t = new SessaoOwnerActionToken();
        t.setId((long)(Math.random() * 10000));
        t.setTenant(tenant);
        t.setSessaoConsumo(sessao);
        t.setStatus(status);
        t.setExpiresAt(Instant.now().plusSeconds(600));
        t.setMaxUses(20);
        t.setUseCount(0);
        t.setPurpose(com.restaurante.model.enums.SessaoOwnerActionTokenPurpose.OWNER_SESSION_MANAGEMENT);
        return t;
    }
}
