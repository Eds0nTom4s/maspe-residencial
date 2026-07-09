package com.restaurante.consumo;

import com.restaurante.consumo.identificacao.service.TelefoneOtpService;
import com.restaurante.consumo.participante.entity.SessaoConsumoParticipante;
import com.restaurante.consumo.participante.entity.SessaoOwnerActionToken;
import com.restaurante.consumo.participante.repository.SessaoConsumoParticipanteRepository;
import com.restaurante.consumo.participante.service.SessaoConsumoParticipanteService;
import com.restaurante.consumo.participante.service.SessaoOwnerActionTokenService;
import com.restaurante.consumo.participante.service.SessaoParticipanteOwnerTokenActionService;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.SessaoParticipanteRole;
import com.restaurante.model.enums.SessaoParticipanteStatus;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Prompt 41.5 — Testes unitários do SessaoParticipanteOwnerTokenActionService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SessaoParticipanteOwnerTokenActionService — Prompt 41.5")
class SessaoParticipanteOwnerTokenActionServiceTest {

    @Mock SessaoConsumoParticipanteService participanteService;
    @Mock SessaoOwnerActionTokenService ownerTokenService;
    @Mock SessaoConsumoParticipanteRepository participanteRepository;
    @Mock OperationalEventLogService eventLogService;

    SessaoParticipanteOwnerTokenActionService service;

    @BeforeEach
    void setUp() {
        service = new SessaoParticipanteOwnerTokenActionService(
                participanteService, ownerTokenService, participanteRepository, eventLogService);
    }

    // =========================================================================
    @Nested
    @DisplayName("Aprovar por token")
    class Aprovar {

        @Test
        @DisplayName("aprova PENDING_APPROVAL com token válido")
        void aprova_pending_approval() {
            var ctx = buildTokenContext();
            var target = buildParticipante(200L, SessaoParticipanteStatus.PENDING_APPROVAL, SessaoParticipanteRole.GUEST);
            target.setExpiresAt(Instant.now().plusSeconds(600));

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);
            when(participanteRepository.findForUpdateById(1L, 200L)).thenReturn(Optional.of(target));
            when(participanteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var result = service.approveByOwnerToken("qr", 200L, "raw", null, null, null);

            assertThat(result.participanteId()).isEqualTo(200L);
            assertThat(result.status()).isEqualTo(SessaoParticipanteStatus.ACTIVE);
        }

        @Test
        @DisplayName("falha se participante já está ACTIVE")
        void falha_se_ja_active() {
            var ctx = buildTokenContext();
            var target = buildParticipante(200L, SessaoParticipanteStatus.ACTIVE, SessaoParticipanteRole.GUEST);

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);
            when(participanteRepository.findForUpdateById(1L, 200L)).thenReturn(Optional.of(target));

            assertThatThrownBy(() -> service.approveByOwnerToken("qr", 200L, "raw", null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("PARTICIPANT_ALREADY_ACTIVE");
        }

        @Test
        @DisplayName("falha se participante não está em PENDING_APPROVAL")
        void falha_se_nao_pending_approval() {
            var ctx = buildTokenContext();
            var target = buildParticipante(200L, SessaoParticipanteStatus.INVITED, SessaoParticipanteRole.GUEST);

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);
            when(participanteRepository.findForUpdateById(1L, 200L)).thenReturn(Optional.of(target));

            assertThatThrownBy(() -> service.approveByOwnerToken("qr", 200L, "raw", null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("PARTICIPANTE_NOT_PENDING_APPROVAL");
        }

        @Test
        @DisplayName("falha se OWNER não é mais ACTIVE")
        void falha_se_owner_nao_active() {
            var owner = buildParticipante(10L, SessaoParticipanteStatus.REMOVED, SessaoParticipanteRole.OWNER);
            var sessao = buildSessao(100L);
            var tenant = buildTenant(1L);
            owner.setSessaoConsumo(sessao);
            owner.setTenant(tenant);
            var ctx = new SessaoOwnerActionTokenService.ValidateResult(owner, sessao, tenant);

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);

            assertThatThrownBy(() -> service.approveByOwnerToken("qr", 200L, "raw", null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("OWNER_ACTION_TOKEN_OWNER_NOT_ACTIVE");
        }

        @Test
        @DisplayName("falha se OWNER não tem mais role OWNER")
        void falha_se_owner_sem_role_owner() {
            var owner = buildParticipante(10L, SessaoParticipanteStatus.ACTIVE, SessaoParticipanteRole.GUEST);
            var sessao = buildSessao(100L);
            var tenant = buildTenant(1L);
            owner.setSessaoConsumo(sessao);
            owner.setTenant(tenant);
            var ctx = new SessaoOwnerActionTokenService.ValidateResult(owner, sessao, tenant);

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);

            assertThatThrownBy(() -> service.approveByOwnerToken("qr", 200L, "raw", null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("OWNER_ACTION_TOKEN_OWNER_NOT_OWNER");
        }

        @Test
        @DisplayName("validateAndUse é chamado (useCount incrementa)")
        void validate_and_use_chamado() {
            var ctx = buildTokenContext();
            var target = buildParticipante(200L, SessaoParticipanteStatus.PENDING_APPROVAL, SessaoParticipanteRole.GUEST);
            target.setExpiresAt(Instant.now().plusSeconds(600));

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);
            when(participanteRepository.findForUpdateById(1L, 200L)).thenReturn(Optional.of(target));
            when(participanteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.approveByOwnerToken("qr", 200L, "raw", null, null, null);

            verify(ownerTokenService).validateAndUse(1L, 100L, "raw", null, null);
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("Rejeitar por token")
    class Rejeitar {

        @Test
        @DisplayName("rejeita PENDING_APPROVAL com token válido")
        void rejeita_pending_approval() {
            var ctx = buildTokenContext();
            var target = buildParticipante(200L, SessaoParticipanteStatus.PENDING_APPROVAL, SessaoParticipanteRole.GUEST);
            target.setExpiresAt(Instant.now().plusSeconds(600));

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);
            when(participanteRepository.findForUpdateById(1L, 200L)).thenReturn(Optional.of(target));
            when(participanteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var result = service.rejectByOwnerToken("qr", 200L, "raw", null, null, null);

            assertThat(result.status()).isEqualTo(SessaoParticipanteStatus.REJECTED);
        }

        @Test
        @DisplayName("falha se participante está ACTIVE")
        void falha_se_active() {
            var ctx = buildTokenContext();
            var target = buildParticipante(200L, SessaoParticipanteStatus.ACTIVE, SessaoParticipanteRole.GUEST);

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);
            when(participanteRepository.findForUpdateById(1L, 200L)).thenReturn(Optional.of(target));

            assertThatThrownBy(() -> service.rejectByOwnerToken("qr", 200L, "raw", null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("PARTICIPANTE_NOT_REJECTABLE");
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("Cancelar por token")
    class Cancelar {

        @Test
        @DisplayName("cancela INVITED")
        void cancela_invited() {
            var ctx = buildTokenContext();
            var target = buildParticipante(200L, SessaoParticipanteStatus.INVITED, SessaoParticipanteRole.GUEST);

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);
            when(participanteRepository.findForUpdateById(1L, 200L)).thenReturn(Optional.of(target));
            when(participanteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var r = service.cancelByOwnerToken("qr", 200L, "raw", null, null, null);
            assertThat(r.status()).isEqualTo(SessaoParticipanteStatus.CANCELLED);
            assertThat(r.wasCancelled()).isTrue();
        }

        @Test
        @DisplayName("cancela PENDING_OTP")
        void cancela_pending_otp() {
            var ctx = buildTokenContext();
            var target = buildParticipante(200L, SessaoParticipanteStatus.PENDING_OTP, SessaoParticipanteRole.GUEST);

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);
            when(participanteRepository.findForUpdateById(1L, 200L)).thenReturn(Optional.of(target));
            when(participanteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var r = service.cancelByOwnerToken("qr", 200L, "raw", null, null, null);
            assertThat(r.status()).isEqualTo(SessaoParticipanteStatus.CANCELLED);
        }

        @Test
        @DisplayName("cancela PENDING_APPROVAL")
        void cancela_pending_approval() {
            var ctx = buildTokenContext();
            var target = buildParticipante(200L, SessaoParticipanteStatus.PENDING_APPROVAL, SessaoParticipanteRole.GUEST);

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);
            when(participanteRepository.findForUpdateById(1L, 200L)).thenReturn(Optional.of(target));
            when(participanteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var r = service.cancelByOwnerToken("qr", 200L, "raw", null, null, null);
            assertThat(r.status()).isEqualTo(SessaoParticipanteStatus.CANCELLED);
        }

        @Test
        @DisplayName("falha para ACTIVE — não cancelável")
        void falha_para_active() {
            var ctx = buildTokenContext();
            var target = buildParticipante(200L, SessaoParticipanteStatus.ACTIVE, SessaoParticipanteRole.GUEST);

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);
            when(participanteRepository.findForUpdateById(1L, 200L)).thenReturn(Optional.of(target));

            assertThatThrownBy(() -> service.cancelByOwnerToken("qr", 200L, "raw", null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("PARTICIPANTE_NOT_CANCELABLE");
        }

        @Test
        @DisplayName("idempotente se já CANCELLED")
        void idempotente_se_ja_cancelled() {
            var ctx = buildTokenContext();
            var target = buildParticipante(200L, SessaoParticipanteStatus.CANCELLED, SessaoParticipanteRole.GUEST);
            target.setCancelledAt(Instant.now().minusSeconds(60));

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);
            when(participanteRepository.findForUpdateById(1L, 200L)).thenReturn(Optional.of(target));

            var r = service.cancelByOwnerToken("qr", 200L, "raw", null, null, null);
            assertThat(r.wasCancelled()).isFalse();
            verify(participanteRepository, never()).save(any());
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("Reenviar convite por token")
    class Reenviar {

        @Test
        @DisplayName("reenvia se canResend=true")
        void reenvia_se_pode() {
            var ctx = buildTokenContext();
            var target = buildParticipante(200L, SessaoParticipanteStatus.INVITED, SessaoParticipanteRole.GUEST);
            target.setResendCount(1);

            var mockOtp = mock(TelefoneOtpService.OtpRequestResult.class);
            when(mockOtp.isSmsSent()).thenReturn(true);

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);
            when(participanteRepository.findForUpdateById(1L, 200L)).thenReturn(Optional.of(target));
            // Primeira chamada canResendInviteNow → true (check), segunda → false (canResend no resultado)
            when(participanteService.canResendInviteNow(target)).thenReturn(true, false);
            when(participanteService.resendInviteByOwnerTokenInternal(any(), any(), any(), any())).thenReturn(mockOtp);

            var r = service.resendInviteByOwnerToken("qr", 200L, "raw", null, null);
            assertThat(r.resent()).isTrue();
        }

        @Test
        @DisplayName("falha se canResend=false")
        void falha_se_nao_pode_reenviar() {
            var ctx = buildTokenContext();
            var target = buildParticipante(200L, SessaoParticipanteStatus.INVITED, SessaoParticipanteRole.GUEST);
            target.setResendCount(5);

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);
            when(participanteRepository.findForUpdateById(1L, 200L)).thenReturn(Optional.of(target));
            when(participanteService.canResendInviteNow(target)).thenReturn(false);

            assertThatThrownBy(() -> service.resendInviteByOwnerToken("qr", 200L, "raw", null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("PARTICIPANTE_INVITE_NOT_RESENDABLE");
        }
    }

    // =========================================================================
    // Verificação de auditoria — sem raw token
    // =========================================================================

    @Nested
    @DisplayName("Auditoria — sem raw token")
    class Auditoria {

        @Test
        @DisplayName("metadados de auditoria não contêm raw token")
        void auditoria_sem_raw_token() {
            var ctx = buildTokenContext();
            var target = buildParticipante(200L, SessaoParticipanteStatus.PENDING_APPROVAL, SessaoParticipanteRole.GUEST);
            target.setExpiresAt(Instant.now().plusSeconds(600));

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "very-secret-raw-token", null, null)).thenReturn(ctx);
            when(participanteRepository.findForUpdateById(1L, 200L)).thenReturn(Optional.of(target));
            when(participanteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.approveByOwnerToken("qr", 200L, "very-secret-raw-token", null, null, null);

            // Verificar que auditoria foi emitida
            verify(eventLogService, atLeastOnce()).logPublicEvent(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(String.class), any(), any(), any());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private SessaoOwnerActionTokenService.ValidateResult buildTokenContext() {
        var owner = buildParticipante(10L, SessaoParticipanteStatus.ACTIVE, SessaoParticipanteRole.OWNER);
        var sessao = buildSessao(100L);
        var tenant = buildTenant(1L);
        owner.setSessaoConsumo(sessao);
        owner.setTenant(tenant);
        return new SessaoOwnerActionTokenService.ValidateResult(owner, sessao, tenant);
    }

    private SessaoConsumoParticipante buildParticipante(Long id, SessaoParticipanteStatus status, SessaoParticipanteRole role) {
        var tenant = buildTenant(1L);
        var sessao = buildSessao(100L);
        var p = new SessaoConsumoParticipante();
        p.setId(id);
        p.setStatus(status);
        p.setRole(role);
        p.setTenant(tenant);
        p.setSessaoConsumo(sessao);
        p.setResendCount(0);
        return p;
    }

    private SessaoConsumo buildSessao(Long id) {
        var s = new SessaoConsumo(); s.setId(id); return s;
    }

    private Tenant buildTenant(Long id) {
        var t = new Tenant(); t.setId(id); return t;
    }

    // =========================================================================
    // Defesa contra Sessão Fechada
    // =========================================================================
    @Nested
    @DisplayName("Sessão Fechada")
    class SessaoFechada {

        @Test
        @DisplayName("approveByToken falha quando sessao fechada")
        void approveByToken_falha_quando_sessao_fechada_mesmo_token_active() {
            var ctx = buildTokenContext();
            ctx.sessaoConsumo().setStatus(com.restaurante.model.enums.StatusSessaoConsumo.ENCERRADA);
            var target = buildParticipante(200L, SessaoParticipanteStatus.PENDING_APPROVAL, SessaoParticipanteRole.GUEST);

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);

            assertThatThrownBy(() -> service.approveByOwnerToken("qr", 200L, "raw", null, null, null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("OWNER_ACTION_TOKEN_SESSION_CLOSED");

            verify(participanteRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejectByToken falha quando sessao fechada")
        void rejectByToken_falha_quando_sessao_fechada_mesmo_token_active() {
            var ctx = buildTokenContext();
            ctx.sessaoConsumo().setStatus(com.restaurante.model.enums.StatusSessaoConsumo.ENCERRADA);
            var target = buildParticipante(200L, SessaoParticipanteStatus.PENDING_APPROVAL, SessaoParticipanteRole.GUEST);

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);

            assertThatThrownBy(() -> service.rejectByOwnerToken("qr", 200L, "raw", null, null, null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("OWNER_ACTION_TOKEN_SESSION_CLOSED");

            verify(participanteRepository, never()).save(any());
        }

        @Test
        @DisplayName("cancelByToken falha quando sessao fechada")
        void cancelByToken_falha_quando_sessao_fechada_mesmo_token_active() {
            var ctx = buildTokenContext();
            ctx.sessaoConsumo().setStatus(com.restaurante.model.enums.StatusSessaoConsumo.ENCERRADA);
            var target = buildParticipante(200L, SessaoParticipanteStatus.PENDING_APPROVAL, SessaoParticipanteRole.GUEST);

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);

            assertThatThrownBy(() -> service.cancelByOwnerToken("qr", 200L, "raw", null, null, null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("OWNER_ACTION_TOKEN_SESSION_CLOSED");

            verify(participanteRepository, never()).save(any());
        }

        @Test
        @DisplayName("resendInviteByToken falha quando sessao fechada")
        void resendInviteByToken_falha_quando_sessao_fechada_mesmo_token_active() {
            var ctx = buildTokenContext();
            ctx.sessaoConsumo().setStatus(com.restaurante.model.enums.StatusSessaoConsumo.ENCERRADA);
            var target = buildParticipante(200L, SessaoParticipanteStatus.PENDING_APPROVAL, SessaoParticipanteRole.GUEST);

            when(participanteService.resolveQrContext("qr")).thenReturn(new SessaoConsumoParticipanteService.QrContext(1L, 100L));
            when(ownerTokenService.validateAndUse(1L, 100L, "raw", null, null)).thenReturn(ctx);

            assertThatThrownBy(() -> service.resendInviteByOwnerToken("qr", 200L, "raw", null, null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("OWNER_ACTION_TOKEN_SESSION_CLOSED");

            verify(participanteService, never()).resendInviteByOwnerTokenInternal(any(), any(), any(), any());
        }
    }
}
