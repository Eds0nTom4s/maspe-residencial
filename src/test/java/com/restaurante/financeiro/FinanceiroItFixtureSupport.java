package com.restaurante.financeiro;

import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.OperationalDeviceType;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.model.enums.TipoUnidadeConsumo;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class FinanceiroItFixtureSupport {

    private final TenantRepository tenantRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final CozinhaRepository cozinhaRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final MesaRepository mesaRepository;
    private final QrCodeOperacionalRepository qrCodeOperacionalRepository;
    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;

    FinanceiroItFixtureSupport(
            TenantRepository tenantRepository,
            InstituicaoRepository instituicaoRepository,
            UnidadeAtendimentoRepository unidadeAtendimentoRepository,
            CozinhaRepository cozinhaRepository,
            DispositivoOperacionalRepository dispositivoOperacionalRepository,
            MesaRepository mesaRepository,
            QrCodeOperacionalRepository qrCodeOperacionalRepository,
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository
    ) {
        this.tenantRepository = tenantRepository;
        this.instituicaoRepository = instituicaoRepository;
        this.unidadeAtendimentoRepository = unidadeAtendimentoRepository;
        this.cozinhaRepository = cozinhaRepository;
        this.dispositivoOperacionalRepository = dispositivoOperacionalRepository;
        this.mesaRepository = mesaRepository;
        this.qrCodeOperacionalRepository = qrCodeOperacionalRepository;
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
    }

    public DispositivoOperacional createPosCaixaDevice(ProvisionarTenantResponse prov, String nome) {
        return createOperationalDevice(prov, nome, DispositivoTipo.CHECKOUT, OperationalDeviceType.POS_CAIXA);
    }

    public DispositivoOperacional createKdsDevice(ProvisionarTenantResponse prov, String nome) {
        return createOperationalDevice(prov, nome, DispositivoTipo.KDS, OperationalDeviceType.KDS_COZINHA);
    }

    public DispositivoOperacional createPosDevice(ProvisionarTenantResponse prov, String nome) {
        return createOperationalDevice(prov, nome, DispositivoTipo.POS, OperationalDeviceType.POS_ATENDIMENTO);
    }

    public DispositivoOperacional createOperationalDevice(
            ProvisionarTenantResponse prov,
            String nome,
            DispositivoTipo tipo,
            OperationalDeviceType operationalType
    ) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        Instituicao inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();

        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setCodigo("IT-" + System.nanoTime());
        d.setNome(nome);
        d.setTipo(tipo);
        d.setOperationalDeviceType(operationalType);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }

    public String ensureMesaQrToken(ProvisionarTenantResponse prov) {
        if (prov.getMesas() != null && !prov.getMesas().isEmpty()) {
            String existing = prov.getMesas().get(0).getQrToken();
            if (existing != null && !existing.isBlank()) {
                return existing;
            }
        }

        Mesa mesa = mesaRepository.findByUnidadeAtendimentoId(prov.getUnidadeAtendimentoId())
                .stream()
                .findFirst()
                .orElseGet(() -> createMesa(prov));

        return qrCodeOperacionalRepository.findFirstByMesaIdAndTenantIdAndAtivoTrueAndRevogadoFalse(mesa.getId(), prov.getTenantId())
                .map(QrCodeOperacional::getToken)
                .orElseGet(() -> createMesaQr(prov, mesa).getToken());
    }

    public Long createTenantUser(ProvisionarTenantResponse prov, TenantUserRole role) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();

        String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        User user = new User();
        user.setUsername(role.name().toLowerCase() + "-" + suffix);
        user.setPassword("{noop}x");
        user.setEmail(role.name().toLowerCase() + "-" + suffix + "@test.local");
        user.setTelefone("+24491" + String.format("%07d", Math.abs(System.nanoTime() % 10_000_000L)));
        user.adicionarRole(Role.ROLE_GERENTE);
        user = userRepository.saveAndFlush(user);

        TenantUser tenantUser = new TenantUser();
        tenantUser.setTenant(tenant);
        tenantUser.setUser(user);
        tenantUser.setRole(role);
        tenantUser.setEstado(TenantUserEstado.ATIVO);
        tenantUserRepository.saveAndFlush(tenantUser);
        return user.getId();
    }

    public void ensureCentralCozinha(ProvisionarTenantResponse prov) {
        boolean alreadyLinked = !cozinhaRepository.findByUnidadeAtendimentoIdAndTipoAndAtiva(
                prov.getUnidadeAtendimentoId(),
                TipoCozinha.CENTRAL,
                true
        ).isEmpty();
        if (alreadyLinked) {
            return;
        }

        UnidadeAtendimento ua = unidadeAtendimentoRepository.findByIdWithCozinhas(prov.getUnidadeAtendimentoId()).orElseThrow();
        Cozinha cozinha = Cozinha.builder()
                .nome("Cozinha CENTRAL")
                .tipo(TipoCozinha.CENTRAL)
                .ativa(true)
                .descricao("Cozinha Central IT")
                .build();
        cozinha = cozinhaRepository.saveAndFlush(cozinha);
        ua.adicionarCozinha(cozinha);
        unidadeAtendimentoRepository.saveAndFlush(ua);
    }

    private Mesa createMesa(ProvisionarTenantResponse prov) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        Instituicao inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();

        Mesa mesa = new Mesa();
        mesa.setTenant(tenant);
        mesa.setInstituicao(inst);
        mesa.setUnidadeAtendimento(ua);
        mesa.setReferencia("Mesa IT " + (System.nanoTime() % 100_000));
        mesa.setNumero((int) (System.nanoTime() % 10_000));
        mesa.setQrCode("MESA-IT-" + System.nanoTime());
        mesa.setCapacidade(4);
        mesa.setTipo(TipoUnidadeConsumo.MESA_FISICA);
        mesa.setAtiva(true);
        return mesaRepository.saveAndFlush(mesa);
    }

    private QrCodeOperacional createMesaQr(ProvisionarTenantResponse prov, Mesa mesa) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        Instituicao inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();

        QrCodeOperacional qr = new QrCodeOperacional();
        qr.setTenant(tenant);
        qr.setInstituicao(inst);
        qr.setUnidadeAtendimento(ua);
        qr.setMesa(mesa);
        qr.setNome("QR " + mesa.getReferencia());
        qr.setTipo(QrCodeOperacionalTipo.MESA);
        qr.setToken("qr-mesa-it-" + System.nanoTime());
        qr.setAtivo(true);
        qr.setRevogado(false);
        return qrCodeOperacionalRepository.saveAndFlush(qr);
    }
}
