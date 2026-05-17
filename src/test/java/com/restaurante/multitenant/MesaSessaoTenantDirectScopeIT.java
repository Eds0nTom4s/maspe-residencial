package com.restaurante.multitenant;

import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TipoSessao;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.main.web-application-type=none"
)
@ActiveProfiles("it-postgres")
class MesaSessaoTenantDirectScopeIT extends PostgresTestcontainersConfig {

    @Autowired TenantProvisioningService provisioningService;
    @Autowired MesaRepository mesaRepository;
    @Autowired SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void mesasAndSessoes_areDirectlyTenantScoped() {
        ProvisionarTenantResponse a = provisionRestaurant("tenant-a", "TA", "owner-a@a.com");
        ProvisionarTenantResponse b = provisionRestaurant("tenant-b", "TB", "owner-b@b.com");

        Tenant tenantA = tenantRepository.findById(a.getTenantId()).orElseThrow();
        Tenant tenantB = tenantRepository.findById(b.getTenantId()).orElseThrow();

        Mesa mesaA = mesaRepository.findByTenantId(tenantA.getId()).stream().findFirst().orElseThrow();
        Mesa mesaB = mesaRepository.findByTenantId(tenantB.getId()).stream().findFirst().orElseThrow();

        assertThat(mesaA.getTenant().getId()).isEqualTo(tenantA.getId());
        assertThat(mesaB.getTenant().getId()).isEqualTo(tenantB.getId());

        Instituicao instA = instituicaoRepository.findById(a.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento uaA = unidadeAtendimentoRepository.findById(a.getUnidadeAtendimentoId()).orElseThrow();
        SessaoConsumo sessaoA = SessaoConsumo.builder()
                .qrCodeSessao(UUID.randomUUID().toString())
                .tenant(tenantA)
                .instituicao(instA)
                .unidadeAtendimento(uaA)
                .mesa(mesaA)
                .status(StatusSessaoConsumo.ABERTA)
                .modoAnonimo(true)
                .tipoSessao(TipoSessao.POS_PAGO)
                .build();
        sessaoA = sessaoConsumoRepository.save(sessaoA);

        assertThat(sessaoA.getTenant().getId()).isEqualTo(tenantA.getId());
        assertThat(sessaoConsumoRepository.findAllByTenantIdAndMesaIdAndStatus(tenantA.getId(), mesaA.getId(), StatusSessaoConsumo.ABERTA))
                .hasSize(1);
        assertThat(sessaoConsumoRepository.findAllByTenantIdAndMesaIdAndStatus(tenantB.getId(), mesaA.getId(), StatusSessaoConsumo.ABERTA))
                .isEmpty();
    }

    private ProvisionarTenantResponse provisionRestaurant(String slug, String tenantCode, String ownerEmail) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + slug)
                                .slug(slug)
                                .tenantCode(tenantCode)
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + slug)
                                .sigla(tenantCode)
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(ownerEmail)
                                .telefone("+244900" + Math.abs(slug.hashCode() % 1_000_000))
                                .criarUsuario(true)
                                .build())
                        .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder()
                                .criarMesas(true)
                                .quantidadeMesas(1)
                                .criarQrPorMesa(false)
                                .build())
                        .build()
        );
    }
}
