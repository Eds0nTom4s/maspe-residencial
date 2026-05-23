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
import com.restaurante.model.enums.SessaoParticipantEntryPolicy;
import com.restaurante.model.enums.SessaoParticipanteRole;
import com.restaurante.model.enums.SessaoParticipanteStatus;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.service.SessaoConsumoService;
import com.restaurante.service.operacional.OperationalEventLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class SessaoConsumoParticipanteServiceTest {

    @Test
    void first_join_becomes_owner_when_session_has_no_principal() {
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
        sessao.setModoAnonimo(true);
        when(sessaoRepo.findByTenantIdAndMesaIdAndStatus(10L, 100L, StatusSessaoConsumo.ABERTA)).thenReturn(Optional.of(sessao));

        when(normalizer.normalizeOrThrow("923000000")).thenReturn("+244923000000");
        when(normalizer.mask("+244923000000")).thenReturn("+244923***000");

        TelefoneOtpChallenge ch = new TelefoneOtpChallenge();
        ch.setId(1L);
        ch.setPurpose(OtpPurpose.ENTRAR_SESSAO_COMPARTILHADA);
        ch.setSessaoConsumo(sessao);
        when(otpService.validateOtpPendingOrThrow(10L, 1L, "923000000", "123456")).thenReturn(ch);

        ClienteConsumo cc = new ClienteConsumo();
        cc.setId(300L);
        cc.setTenant(tenant);
        cc.setTelefone("923000000");
        cc.setTelefoneNormalizado("+244923000000");
        cc.setStatus(ClienteConsumoStatus.ACTIVE);
        when(clienteService.getOrCreateByPhone(eq(tenant), eq("923000000"), eq("+244923000000")))
                .thenReturn(new ClienteConsumoService.GetOrCreateResult(cc, true));
        when(clienteService.markPhoneVerified(any())).thenReturn(cc);

        when(partRepo.listBySessaoAndStatus(10L, 200L, SessaoParticipanteStatus.ACTIVE)).thenReturn(List.of());
        when(partRepo.findForUpdateBySessaoAndCliente(10L, 200L, 300L)).thenReturn(Optional.empty());

        ArgumentCaptor<SessaoConsumoParticipante> partCaptor = ArgumentCaptor.forClass(SessaoConsumoParticipante.class);
        when(partRepo.save(partCaptor.capture())).thenAnswer(inv -> {
            SessaoConsumoParticipante p = partCaptor.getValue();
            p.setId(400L);
            return p;
        });
        when(sessaoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(otpService.consumeChallenge(10L, 1L)).thenReturn(ch);

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

        var out = svc.verifyJoinOtp("qr-token", 1L, "923000000", "123456", "Ana", null, null);
        assertThat(out.role()).isEqualTo(SessaoParticipanteRole.OWNER);
        assertThat(out.status()).isEqualTo(SessaoParticipanteStatus.ACTIVE);
        assertThat(out.participanteId()).isEqualTo(400L);
        assertThat(out.clienteConsumoId()).isEqualTo(300L);
    }

    @Test
    void join_is_member_when_session_has_principal_owner() {
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

        ClienteConsumo owner = new ClienteConsumo();
        owner.setId(1L);
        owner.setTenant(tenant);
        owner.setTelefoneNormalizado("+244923999999");

        SessaoConsumo sessao = new SessaoConsumo();
        sessao.setId(200L);
        sessao.setTenant(tenant);
        sessao.setStatus(StatusSessaoConsumo.ABERTA);
        sessao.setModoAnonimo(true);
        sessao.setClienteConsumo(owner);
        when(sessaoRepo.findByTenantIdAndMesaIdAndStatus(10L, 100L, StatusSessaoConsumo.ABERTA)).thenReturn(Optional.of(sessao));

        when(normalizer.normalizeOrThrow("923000000")).thenReturn("+244923000000");
        when(normalizer.mask("+244923000000")).thenReturn("+244923***000");

        TelefoneOtpChallenge ch = new TelefoneOtpChallenge();
        ch.setId(1L);
        ch.setPurpose(OtpPurpose.ENTRAR_SESSAO_COMPARTILHADA);
        ch.setSessaoConsumo(sessao);
        when(otpService.validateOtpPendingOrThrow(10L, 1L, "923000000", "123456")).thenReturn(ch);

        ClienteConsumo joiner = new ClienteConsumo();
        joiner.setId(300L);
        joiner.setTenant(tenant);
        joiner.setTelefone("923000000");
        joiner.setTelefoneNormalizado("+244923000000");
        joiner.setStatus(ClienteConsumoStatus.ACTIVE);
        when(clienteService.getOrCreateByPhone(eq(tenant), eq("923000000"), eq("+244923000000")))
                .thenReturn(new ClienteConsumoService.GetOrCreateResult(joiner, true));
        when(clienteService.markPhoneVerified(any())).thenReturn(joiner);

        when(partRepo.findByTenant_IdAndSessaoConsumo_IdAndClienteConsumo_Id(10L, 200L, 1L)).thenReturn(Optional.of(new SessaoConsumoParticipante()));
        when(partRepo.findForUpdateBySessaoAndCliente(10L, 200L, 300L)).thenReturn(Optional.empty());

        when(partRepo.save(any())).thenAnswer(inv -> {
            SessaoConsumoParticipante p = inv.getArgument(0);
            if (p.getId() == null) p.setId(400L);
            return p;
        });
        when(otpService.consumeChallenge(10L, 1L)).thenReturn(ch);

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

        var out = svc.verifyJoinOtp("qr-token", 1L, "923000000", "123456", "Ana", null, null);
        assertThat(out.role()).isEqualTo(SessaoParticipanteRole.MEMBER);
        assertThat(out.status()).isEqualTo(SessaoParticipanteStatus.ACTIVE);
    }

    @Test
    void join_sets_pending_approval_when_session_policy_requires_owner_approval() {
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
        sessao.setModoAnonimo(true);
        sessao.setParticipantEntryPolicy(SessaoParticipantEntryPolicy.OTP_REQUIRES_OWNER_APPROVAL);
        when(sessaoRepo.findByTenantIdAndMesaIdAndStatus(10L, 100L, StatusSessaoConsumo.ABERTA)).thenReturn(Optional.of(sessao));

        when(normalizer.normalizeOrThrow("923000000")).thenReturn("+244923000000");
        when(normalizer.mask("+244923000000")).thenReturn("+244923***000");

        TelefoneOtpChallenge ch = new TelefoneOtpChallenge();
        ch.setId(1L);
        ch.setPurpose(OtpPurpose.ENTRAR_SESSAO_COMPARTILHADA);
        ch.setSessaoConsumo(sessao);
        when(otpService.validateOtpPendingOrThrow(10L, 1L, "923000000", "123456")).thenReturn(ch);

        ClienteConsumo cc = new ClienteConsumo();
        cc.setId(300L);
        cc.setTenant(tenant);
        cc.setTelefone("923000000");
        cc.setTelefoneNormalizado("+244923000000");
        cc.setStatus(ClienteConsumoStatus.ACTIVE);
        when(clienteService.getOrCreateByPhone(eq(tenant), eq("923000000"), eq("+244923000000")))
                .thenReturn(new ClienteConsumoService.GetOrCreateResult(cc, true));
        when(clienteService.markPhoneVerified(any())).thenReturn(cc);

        when(partRepo.listBySessaoAndStatus(10L, 200L, SessaoParticipanteStatus.ACTIVE)).thenReturn(List.of());
        when(partRepo.findForUpdateBySessaoAndCliente(10L, 200L, 300L)).thenReturn(Optional.empty());

        ArgumentCaptor<SessaoConsumoParticipante> partCaptor = ArgumentCaptor.forClass(SessaoConsumoParticipante.class);
        when(partRepo.save(partCaptor.capture())).thenAnswer(inv -> {
            SessaoConsumoParticipante p = partCaptor.getValue();
            p.setId(400L);
            return p;
        });
        when(otpService.consumeChallenge(10L, 1L)).thenReturn(ch);

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

        var out = svc.verifyJoinOtp("qr-token", 1L, "923000000", "123456", "Ana", null, null);
        assertThat(out.status()).isEqualTo(SessaoParticipanteStatus.PENDING_APPROVAL);
        assertThat(partCaptor.getValue().getApprovalRequestedAt()).isNotNull();
    }

    @Test
    void join_request_is_blocked_when_session_policy_is_invite_only() {
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
        sessao.setModoAnonimo(true);
        sessao.setParticipantEntryPolicy(SessaoParticipantEntryPolicy.INVITE_ONLY);
        when(sessaoRepo.findByTenantIdAndMesaIdAndStatus(10L, 100L, StatusSessaoConsumo.ABERTA)).thenReturn(Optional.of(sessao));

        when(normalizer.normalizeOrThrow("923000000")).thenReturn("+244923000000");
        when(normalizer.mask("+244923000000")).thenReturn("+244923***000");

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

        assertThatThrownBy(() -> svc.requestJoinByQr("qr-token", "923000000", "Ana", null, null))
                .isInstanceOf(com.restaurante.exception.BusinessException.class)
                .hasMessage("SESSION_INVITE_ONLY");
    }
}
