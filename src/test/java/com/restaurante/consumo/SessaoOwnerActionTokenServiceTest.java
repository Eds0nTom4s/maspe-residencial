package com.restaurante.consumo;

import com.restaurante.config.SessaoOwnerActionTokenProperties;
import com.restaurante.consumo.participante.entity.SessaoConsumoParticipante;
import com.restaurante.consumo.participante.entity.SessaoOwnerActionToken;
import com.restaurante.consumo.participante.repository.SessaoOwnerActionTokenRepository;
import com.restaurante.consumo.participante.service.SessaoOwnerActionTokenService;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.SessaoOwnerActionTokenStatus;
import com.restaurante.service.operacional.OperationalEventLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prompt 41.4 — Testes do SessaoOwnerActionTokenService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SessaoOwnerActionTokenService — Prompt 41.4")
class SessaoOwnerActionTokenServiceTest {

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

    // =========================================================================
    @Nested
    @DisplayName("Emissão de token")
    class Emissao {

        @Test
        @DisplayName("emite token após OTP — retorna rawToken não nulo")
        void emite_token_apos_otp_retorna_raw_token() {
            var owner = buildOwner();
            var saved = buildToken(owner, SessaoOwnerActionTokenStatus.ACTIVE);
            when(tokenRepository.save(any())).thenReturn(saved);

            var result = service.issueAfterOwnerOtp(owner, "127.0.0.1", "Mozilla");

            assertThat(result.rawToken()).isNotNull().isNotBlank();
            assertThat(result.expiresAt()).isAfter(Instant.now());
            assertThat(result.maxUses()).isEqualTo(20);
            assertThat(result.ownerParticipanteId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("rawToken gerado é diferente a cada chamada")
        void raw_tokens_sao_distintos() {
            String t1 = service.generateSecureToken();
            String t2 = service.generateSecureToken();
            assertThat(t1).isNotEqualTo(t2);
        }

        @Test
        @DisplayName("hash com mesmo input é igual (determinístico)")
        void hash_e_deterministico() {
            String h1 = service.hashToken("meu-token-teste");
            String h2 = service.hashToken("meu-token-teste");
            assertThat(h1).isEqualTo(h2);
        }

        @Test
        @DisplayName("hash de tokens distintos é diferente")
        void hash_de_tokens_distintos_e_diferente() {
            String h1 = service.hashToken("token-a");
            String h2 = service.hashToken("token-b");
            assertThat(h1).isNotEqualTo(h2);
        }

        @Test
        @DisplayName("pepper influencia o hash")
        void pepper_influencia_hash() {
            SessaoOwnerActionTokenProperties propsComPepper = org.mockito.Mockito.mock(SessaoOwnerActionTokenProperties.class);
            when(propsComPepper.getTtlMinutes()).thenReturn(10);
            when(propsComPepper.getMaxUses()).thenReturn(20);
            when(propsComPepper.getHashPepper()).thenReturn("outro-pepper");
            var svc2 = new SessaoOwnerActionTokenService(tokenRepository, propsComPepper, eventLogService);

            String h1 = service.hashToken("meu-token");
            String h2 = svc2.hashToken("meu-token");
            assertThat(h1).isNotEqualTo(h2);
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("Validação e uso de token")
    class ValidacaoEUso {

        @Test
        @DisplayName("token válido incrementa useCount")
        void token_valido_incrementa_use_count() {
            var owner = buildOwner();
            var token = buildToken(owner, SessaoOwnerActionTokenStatus.ACTIVE);
            token.setUseCount(0);
            token.setMaxUses(20);
            token.setExpiresAt(Instant.now().plusSeconds(600));

            String raw = "meu-raw-token";
            String hash = service.hashToken(raw);
            token.setTokenHash(hash);

            when(tokenRepository.findForUpdateByHash(1L, hash)).thenReturn(Optional.of(token));
            when(tokenRepository.save(any())).thenReturn(token);

            service.validateAndUse(1L, 100L, raw, "127.0.0.1", "ua");

            ArgumentCaptor<SessaoOwnerActionToken> captor = ArgumentCaptor.forClass(SessaoOwnerActionToken.class);
            verify(tokenRepository).save(captor.capture());
            assertThat(captor.getValue().getUseCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("token expirado lança OWNER_ACTION_TOKEN_EXPIRED")
        void token_expirado_lanca_excecao() {
            var owner = buildOwner();
            var token = buildToken(owner, SessaoOwnerActionTokenStatus.ACTIVE);
            token.setExpiresAt(Instant.now().minusSeconds(60)); // expirado
            String raw = "meu-raw-token";
            String hash = service.hashToken(raw);
            token.setTokenHash(hash);
            when(tokenRepository.findForUpdateByHash(1L, hash)).thenReturn(Optional.of(token));
            when(tokenRepository.save(any())).thenReturn(token);

            assertThatThrownBy(() -> service.validateAndUse(1L, 100L, raw, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("OWNER_ACTION_TOKEN_EXPIRED");
        }

        @Test
        @DisplayName("token revogado lança OWNER_ACTION_TOKEN_REVOKED")
        void token_revogado_lanca_excecao() {
            var owner = buildOwner();
            var token = buildToken(owner, SessaoOwnerActionTokenStatus.REVOKED);
            token.setExpiresAt(Instant.now().plusSeconds(600));
            String raw = "raw";
            String hash = service.hashToken(raw);
            token.setTokenHash(hash);
            when(tokenRepository.findForUpdateByHash(1L, hash)).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> service.validateAndUse(1L, 100L, raw, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("OWNER_ACTION_TOKEN_REVOKED");
        }

        @Test
        @DisplayName("token de sessão diferente lança OWNER_ACTION_TOKEN_SESSION_MISMATCH")
        void token_sessao_diferente_lanca_excecao() {
            var owner = buildOwner(); // sessao.id = 100
            var token = buildToken(owner, SessaoOwnerActionTokenStatus.ACTIVE);
            token.setExpiresAt(Instant.now().plusSeconds(600));
            String raw = "raw";
            String hash = service.hashToken(raw);
            token.setTokenHash(hash);
            when(tokenRepository.findForUpdateByHash(1L, hash)).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> service.validateAndUse(1L, 999L, raw, null, null)) // sessão diferente
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("OWNER_ACTION_TOKEN_SESSION_MISMATCH");
        }

        @Test
        @DisplayName("token inexistente lança OWNER_ACTION_TOKEN_INVALID")
        void token_inexistente_lanca_excecao() {
            when(tokenRepository.findForUpdateByHash(anyLong(), anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.validateAndUse(1L, 100L, "nao-existe", null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("OWNER_ACTION_TOKEN_INVALID");
        }

        @Test
        @DisplayName("rawToken nulo lança OWNER_ACTION_TOKEN_REQUIRED")
        void raw_token_nulo_lanca_required() {
            assertThatThrownBy(() -> service.validateAndUse(1L, 100L, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("OWNER_ACTION_TOKEN_REQUIRED");
        }

        @Test
        @DisplayName("token com maxUses atingido lança OWNER_ACTION_TOKEN_MAX_USES_EXCEEDED")
        void token_max_uses_lanca_excecao() {
            var owner = buildOwner();
            var token = buildToken(owner, SessaoOwnerActionTokenStatus.ACTIVE);
            token.setExpiresAt(Instant.now().plusSeconds(600));
            token.setMaxUses(5);
            token.setUseCount(5); // já no limite
            String raw = "raw";
            String hash = service.hashToken(raw);
            token.setTokenHash(hash);
            when(tokenRepository.findForUpdateByHash(1L, hash)).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> service.validateAndUse(1L, 100L, raw, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("OWNER_ACTION_TOKEN_MAX_USES_EXCEEDED");
        }

        @Test
        @DisplayName("ao atingir maxUses no último uso, token fica CONSUMED")
        void token_consumed_ao_atingir_max_uses() {
            var owner = buildOwner();
            var token = buildToken(owner, SessaoOwnerActionTokenStatus.ACTIVE);
            token.setExpiresAt(Instant.now().plusSeconds(600));
            token.setMaxUses(3);
            token.setUseCount(2); // próximo uso = 3 = maxUses → CONSUMED
            String raw = "raw";
            String hash = service.hashToken(raw);
            token.setTokenHash(hash);
            when(tokenRepository.findForUpdateByHash(1L, hash)).thenReturn(Optional.of(token));
            when(tokenRepository.save(any())).thenReturn(token);

            service.validateAndUse(1L, 100L, raw, null, null);

            ArgumentCaptor<SessaoOwnerActionToken> captor = ArgumentCaptor.forClass(SessaoOwnerActionToken.class);
            verify(tokenRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(SessaoOwnerActionTokenStatus.CONSUMED);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private SessaoConsumoParticipante buildOwner() {
        var tenant = new Tenant();
        tenant.setId(1L);

        var sessao = new SessaoConsumo();
        sessao.setId(100L);

        var owner = new SessaoConsumoParticipante();
        owner.setId(10L);
        owner.setTenant(tenant);
        owner.setSessaoConsumo(sessao);

        var cli = new com.restaurante.consumo.identificacao.entity.ClienteConsumo();
        cli.setId(5L);
        owner.setClienteConsumo(cli);

        return owner;
    }

    private SessaoOwnerActionToken buildToken(SessaoConsumoParticipante owner, SessaoOwnerActionTokenStatus status) {
        var token = new SessaoOwnerActionToken();
        token.setId(1L);
        token.setTenant(owner.getTenant());
        token.setSessaoConsumo(owner.getSessaoConsumo());
        token.setOwnerParticipante(owner);
        token.setClienteConsumo(owner.getClienteConsumo());
        token.setStatus(status);
        token.setPurpose(com.restaurante.model.enums.SessaoOwnerActionTokenPurpose.OWNER_SESSION_MANAGEMENT);
        token.setExpiresAt(Instant.now().plusSeconds(600));
        token.setMaxUses(20);
        token.setUseCount(0);
        return token;
    }
}
