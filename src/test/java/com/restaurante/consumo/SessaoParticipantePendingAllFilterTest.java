package com.restaurante.consumo;

import com.restaurante.config.SessaoParticipanteListProperties;
import com.restaurante.consumo.participante.entity.SessaoConsumoParticipante;
import com.restaurante.consumo.participante.entity.SessaoParticipanteLifecycleJobRun;
import com.restaurante.consumo.participante.repository.SessaoConsumoParticipanteRepository;
import com.restaurante.consumo.participante.repository.SessaoParticipanteLifecycleJobRunRepository;
import com.restaurante.consumo.participante.service.SessaoOwnerActionTokenService;
import com.restaurante.consumo.participante.service.SessaoParticipanteExtendedListService;
import com.restaurante.consumo.participante.service.SessaoConsumoParticipanteService;
import com.restaurante.consumo.identificacao.service.TelefoneNormalizerService;
import com.restaurante.config.SessaoParticipanteLifecycleProperties;
import com.restaurante.config.SessaoOwnerActionTokenProperties;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.enums.SessaoParticipanteStatus;
import com.restaurante.service.operacional.OperationalEventLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Prompt 41.4 — Testes de listagem paginada, pending-all e filtros.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SessaoParticipanteExtendedListService — Prompt 41.4")
class SessaoParticipantePendingAllFilterTest {

    @Mock SessaoConsumoParticipanteRepository participanteRepository;
    @Mock SessaoParticipanteLifecycleJobRunRepository jobRunRepository;
    @Mock SessaoOwnerActionTokenService ownerActionTokenService;
    @Mock SessaoConsumoParticipanteService participanteService;
    @Mock TelefoneNormalizerService telefoneNormalizerService;
    @Mock SessaoParticipanteListProperties listProps;
    @Mock SessaoParticipanteLifecycleProperties lifecycleProps;
    @Mock SessaoOwnerActionTokenProperties tokenProps;
    @Mock OperationalEventLogService eventLogService;

    SessaoParticipanteExtendedListService service;

    @BeforeEach
    void setUp() {
        when(listProps.getMaxPageSize()).thenReturn(100);
        when(listProps.getDefaultPageSize()).thenReturn(20);
        service = new SessaoParticipanteExtendedListService(
                participanteRepository, jobRunRepository, ownerActionTokenService,
                participanteService, telefoneNormalizerService,
                listProps, lifecycleProps, tokenProps, eventLogService
        );
    }

    // =========================================================================
    @Nested
    @DisplayName("Validação de paginação")
    class PaginacaoValidacao {

        @Test
        @DisplayName("tamanho acima do máximo lança PAGE_SIZE_TOO_LARGE")
        void size_acima_do_maximo_lanca_excecao() {
            assertThatThrownBy(() ->
                    service.listAllByDevice(1L, 100L, null, null, 0, 101)
            ).isInstanceOf(BusinessException.class)
             .hasMessageContaining("PAGE_SIZE_TOO_LARGE");
        }

        @Test
        @DisplayName("tamanho dentro do limite aceita normalmente")
        void size_dentro_do_limite_aceita() {
            Page<SessaoConsumoParticipante> emptyPage = new PageImpl<>(Collections.emptyList());
            when(participanteRepository.listBySessaoPaged(anyLong(), anyLong(), any(), any(), any(Pageable.class)))
                    .thenReturn(emptyPage);
            when(participanteService.canResendInviteNow(any())).thenReturn(false);

            var result = service.listAllByDevice(1L, 100L, null, null, 0, 50);
            assertThat(result.items()).isEmpty();
            assertThat(result.size()).isLessThanOrEqualTo(100);
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("Pending-All — filtros por status")
    class PendingAllFiltros {

        @Test
        @DisplayName("status inválido lança INVALID_PARTICIPANT_STATUS_FILTER")
        void status_invalido_lanca_excecao() {
            var tokenResult = buildTokenResult();

            assertThatThrownBy(() ->
                    service.listPendingAll(1L, 100L, tokenResult, List.of("STATUS_INVALIDO"), null, 0, 20, null, null)
            ).isInstanceOf(BusinessException.class)
             .hasMessageContaining("INVALID_PARTICIPANT_STATUS_FILTER");
        }

        @Test
        @DisplayName("sem filtro de status retorna INVITED, PENDING_OTP, PENDING_APPROVAL por defeito")
        void sem_filtro_retorna_defaults() {
            var tokenResult = buildTokenResult();
            Page<SessaoConsumoParticipante> emptyPage = new PageImpl<>(Collections.emptyList());
            when(participanteRepository.listBySessaoAndStatuses(anyLong(), anyLong(), anyCollection(), any(Pageable.class)))
                    .thenReturn(emptyPage);

            var result = service.listPendingAll(1L, 100L, tokenResult, null, null, 0, 20, null, null);
            assertThat(result.items()).isEmpty();
        }

        @Test
        @DisplayName("filtro por INVITED funciona")
        void filtro_por_invited_funciona() {
            var tokenResult = buildTokenResult();
            var participante = buildParticipante(SessaoParticipanteStatus.INVITED);
            Page<SessaoConsumoParticipante> page = new PageImpl<>(List.of(participante));
            when(participanteRepository.listBySessaoAndStatuses(anyLong(), anyLong(), anyCollection(), any(Pageable.class)))
                    .thenReturn(page);
            when(participanteService.canResendInviteNow(any())).thenReturn(true);
            when(telefoneNormalizerService.mask(any())).thenReturn("***");

            var result = service.listPendingAll(1L, 100L, tokenResult, List.of("INVITED"), null, 0, 20, null, null);
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).status()).isEqualTo(SessaoParticipanteStatus.INVITED);
        }

        @Test
        @DisplayName("filtro canResend=true exclui participantes que não podem reenviar")
        void filtro_can_resend_exclui_nao_podem() {
            var tokenResult = buildTokenResult();
            var p1 = buildParticipante(SessaoParticipanteStatus.INVITED);
            var p2 = buildParticipante(SessaoParticipanteStatus.INVITED);
            Page<SessaoConsumoParticipante> page = new PageImpl<>(List.of(p1, p2));
            when(participanteRepository.listBySessaoAndStatuses(anyLong(), anyLong(), anyCollection(), any(Pageable.class)))
                    .thenReturn(page);
            when(participanteService.canResendInviteNow(p1)).thenReturn(true);
            when(participanteService.canResendInviteNow(p2)).thenReturn(false);
            when(telefoneNormalizerService.mask(any())).thenReturn("***");

            var result = service.listPendingAll(1L, 100L, tokenResult, null, Boolean.TRUE, 0, 20, null, null);
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).canResend()).isTrue();
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("Health check do job")
    class HealthCheck {

        @Test
        @DisplayName("sem runs retorna jobEnabled e config")
        void sem_runs_retorna_config() {
            when(jobRunRepository.findLastRunsByJobName(any(), any())).thenReturn(Collections.emptyList());
            when(lifecycleProps.isExpirationJobEnabled()).thenReturn(true);
            when(lifecycleProps.getInviteTtlMinutes()).thenReturn(30);
            when(lifecycleProps.getPendingApprovalTtlMinutes()).thenReturn(60);
            when(lifecycleProps.getExpirationBatchSize()).thenReturn(200);

            var health = service.getJobHealth(null, null, null);

            assertThat(health.jobEnabled()).isTrue();
            assertThat(health.lastRunAt()).isNull();
            assertThat(health.config().inviteTtlMinutes()).isEqualTo(30);
        }

        @Test
        @DisplayName("com run retorna último status e expiredCount")
        void com_run_retorna_status() {
            var run = new SessaoParticipanteLifecycleJobRun();
            run.setStatus("SUCCESS");
            run.setExpiredCount(5);
            run.setScannedCount(10);
            run.setStartedAt(java.time.Instant.now().minusSeconds(60));

            when(jobRunRepository.findLastRunsByJobName(any(), any())).thenReturn(List.of(run));
            when(lifecycleProps.isExpirationJobEnabled()).thenReturn(true);
            when(lifecycleProps.getInviteTtlMinutes()).thenReturn(30);
            when(lifecycleProps.getPendingApprovalTtlMinutes()).thenReturn(60);
            when(lifecycleProps.getExpirationBatchSize()).thenReturn(200);

            var health = service.getJobHealth(null, null, null);

            assertThat(health.lastStatus()).isEqualTo("SUCCESS");
            assertThat(health.lastExpiredCount()).isEqualTo(5);
            assertThat(health.lastScannedCount()).isEqualTo(10);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private SessaoOwnerActionTokenService.ValidateResult buildTokenResult() {
        var tenant = new com.restaurante.model.entity.Tenant(); tenant.setId(1L);
        var sessao = new com.restaurante.model.entity.SessaoConsumo(); sessao.setId(100L);
        var owner = new com.restaurante.consumo.participante.entity.SessaoConsumoParticipante();
        owner.setId(10L);
        return new SessaoOwnerActionTokenService.ValidateResult(owner, sessao, tenant);
    }

    private SessaoConsumoParticipante buildParticipante(SessaoParticipanteStatus status) {
        var p = new SessaoConsumoParticipante();
        p.setId((long) (Math.random() * 1000));
        p.setStatus(status);
        p.setTelefoneNormalizado("+244923000000");
        p.setCreatedAt(java.time.Instant.now());
        return p;
    }
}
