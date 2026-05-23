package com.restaurante.consumo;

import com.restaurante.consumo.identificacao.entity.ClienteConsumo;
import com.restaurante.consumo.identificacao.entity.TelefoneOtpChallenge;
import com.restaurante.consumo.identificacao.service.ClienteConsumoService;
import com.restaurante.consumo.identificacao.service.TelefoneNormalizerService;
import com.restaurante.consumo.identificacao.service.TelefoneOtpService;
import com.restaurante.consumo.participante.entity.SessaoConsumoParticipante;
import com.restaurante.consumo.participante.repository.SessaoConsumoParticipanteRepository;
import com.restaurante.consumo.participante.service.SessaoConsumoParticipanteService;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.ClienteConsumoStatus;
import com.restaurante.model.enums.OtpPurpose;
import com.restaurante.model.enums.SessaoParticipanteRole;
import com.restaurante.model.enums.SessaoParticipanteStatus;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.service.SessaoConsumoService;
import com.restaurante.service.operacional.OperationalEventLogService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class SessaoParticipanteOwnerApprovalServiceTest {

    @Test
    void owner_can_approve_pending_participant_using_owner_auth_otp() {
        QrCodeOperacionalService qrService = Mockito.mock(QrCodeOperacionalService.class);
        SessaoConsumoService sessaoService = Mockito.mock(SessaoConsumoService.class);
        SessaoConsumoRepository sessaoRepo = Mockito.mock(SessaoConsumoRepository.class);
        SessaoConsumoParticipanteRepository partRepo = Mockito.mock(SessaoConsumoParticipanteRepository.class);
        DispositivoOperacionalRepository dispRepo = Mockito.mock(DispositivoOperacionalRepository.class);
        TelefoneNormalizerService normalizer = Mockito.mock(TelefoneNormalizerService.class);
        TelefoneOtpService otpService = Mockito.mock(TelefoneOtpService.class);
        ClienteConsumoService clienteService = Mockito.mock(ClienteConsumoService.class);
        OperationalEventLogService audit = Mockito.mock(OperationalEventLogService.class);

        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setTenantCode("t-10");

        Mesa mesa = new Mesa();
        mesa.setId(100L);
        Instituicao inst = new Instituicao();
        inst.setId(50L);

        QrCodeOperacional qr = new QrCodeOperacional();
        qr.setTenant(tenant);
        qr.setInstituicao(inst);
        qr.setMesa(mesa);
        when(qrService.resolverOperacionalAtivoParaOperacao("qr-token")).thenReturn(qr);

        SessaoConsumo sessao = new SessaoConsumo();
        sessao.setId(200L);
        sessao.setTenant(tenant);
        sessao.setStatus(StatusSessaoConsumo.ABERTA);
        when(sessaoRepo.findByTenantIdAndMesaIdAndStatus(10L, 100L, StatusSessaoConsumo.ABERTA)).thenReturn(Optional.of(sessao));

        when(normalizer.normalizeOrThrow("923999999")).thenReturn("+244923999999");
        when(normalizer.mask("+244923999999")).thenReturn("+244923***999");

        SessaoConsumoParticipante owner = new SessaoConsumoParticipante();
        owner.setId(1L);
        owner.setTenant(tenant);
        owner.setSessaoConsumo(sessao);
        owner.setTelefoneNormalizado("+244923999999");
        owner.setRole(SessaoParticipanteRole.OWNER);
        owner.setStatus(SessaoParticipanteStatus.ACTIVE);
        when(partRepo.findByTenant_IdAndSessaoConsumo_IdAndTelefoneNormalizadoAndRoleAndStatus(
                10L, 200L, "+244923999999", SessaoParticipanteRole.OWNER, SessaoParticipanteStatus.ACTIVE
        )).thenReturn(Optional.of(owner));

        TelefoneOtpChallenge ownerCh = new TelefoneOtpChallenge();
        ownerCh.setId(10L);
        ownerCh.setPurpose(OtpPurpose.OWNER_APPROVAL_AUTH);
        ownerCh.setSessaoConsumo(sessao);
        when(otpService.validateOtpPendingOrThrow(10L, 10L, "923999999", "123456")).thenReturn(ownerCh);
        when(otpService.consumeChallenge(10L, 10L)).thenReturn(ownerCh);

        ClienteConsumo pendingCliente = new ClienteConsumo();
        pendingCliente.setId(300L);
        pendingCliente.setTenant(tenant);
        pendingCliente.setTelefoneNormalizado("+244923000000");
        pendingCliente.setStatus(ClienteConsumoStatus.ACTIVE);

        SessaoConsumoParticipante pending = new SessaoConsumoParticipante();
        pending.setId(2L);
        pending.setTenant(tenant);
        pending.setSessaoConsumo(sessao);
        pending.setClienteConsumo(pendingCliente);
        pending.setStatus(SessaoParticipanteStatus.PENDING_APPROVAL);
        when(partRepo.findForUpdateById(10L, 2L)).thenReturn(Optional.of(pending));

        when(partRepo.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));

        SessaoConsumoParticipanteService svc = new SessaoConsumoParticipanteService(
                qrService,
                sessaoService,
                sessaoRepo,
                partRepo,
                dispRepo,
                normalizer,
                otpService,
                clienteService,
                audit
        );

        SessaoConsumoParticipante out = svc.approveByOwner("qr-token", 2L, 10L, "923999999", "123456", "ok", null, null);
        assertThat(out.getStatus()).isEqualTo(SessaoParticipanteStatus.ACTIVE);
        assertThat(out.getApprovedByParticipanteId()).isEqualTo(1L);
        assertThat(out.getApprovalDecidedAt()).isNotNull();
        assertThat(out.getApprovalReason()).isEqualTo("ok");
        assertThat(out.getLastActivityAt()).isNotNull();
        assertThat(out.getJoinedAt()).isNotNull();
        assertThat(out.getLastActivityAt()).isAfterOrEqualTo(out.getJoinedAt());
    }
}

