package com.restaurante.provisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.PlanoRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.main.web-application-type=none")
@ActiveProfiles("it-postgres")
class TenantProvisioningRollbackIT extends PostgresTestcontainersConfig {

    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired PlanoRepository planoRepository;
    @Autowired SubscricaoRepository subscricaoRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void failureMidway_rollsBackEverything() {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        // Pré-cria uma instituição com sigla "DUP" para provocar violação UNIQUE na criação da nova Instituicao.
        Tenant t0 = new Tenant();
        t0.setNome("T0");
        t0.setSlug("t0");
        t0.setTenantCode("T0X");
        t0.setTipo(TenantTipo.RESTAURANTE);
        t0.setEstado(TenantEstado.ATIVO);
        t0 = tenantRepository.saveAndFlush(t0);

        Plano piloto = planoRepository.findByCodigo("PILOTO").orElseThrow();
        Subscricao s0 = new Subscricao();
        s0.setTenant(t0);
        s0.setPlano(piloto);
        s0.setEstado(SubscricaoEstado.ATIVA);
        s0.setInicioEm(java.time.LocalDate.now());
        subscricaoRepository.saveAndFlush(s0);

        Instituicao existing = new Instituicao();
        existing.setTenant(t0);
        existing.setNome("Existing");
        existing.setSigla("DUP");
        existing.setNif("NIF-DUP-0");
        existing.setTelefoneAutorizacao("+244900111111");
        existing.setAtiva(true);
        instituicaoRepository.saveAndFlush(existing);

        ProvisionarTenantRequest req = ProvisionarTenantRequest.builder()
                .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                        .nome("Tenant Rollback")
                        .slug("tenant-rollback")
                        .tenantCode("RBK")
                        .tipo(TenantTipo.VENDEDOR_RUA)
                        .build())
                .planoCodigo("PILOTO")
                .templateCodigo("VENDEDOR_RUA")
                .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                        .nome("Inst Rollback")
                        .sigla("DUP") // força violação UNIQUE
                        .nif("NIF-RBK-1")
                        .telefone("+244900222222")
                        .build())
                .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder()
                        .ativarTenant(true)
                        .criarCategoriaDefault(true)
                        .criarQrPrincipal(true)
                        .criarUnidadeAtendimentoDefault(true)
                        .build())
                .build();

        assertThatThrownBy(() -> provisioningService.provisionar(req))
                .isInstanceOf(Exception.class);

        assertThat(tenantRepository.findBySlug("tenant-rollback")).isEmpty();
        assertThat(tenantRepository.findByTenantCode("RBK")).isEmpty();
    }
}

