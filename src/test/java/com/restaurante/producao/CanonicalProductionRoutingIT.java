package com.restaurante.producao;

import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.UnidadeProducao;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.model.enums.UnidadeProducaoTipo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.service.producao.RotaProducaoService;
import com.restaurante.service.producao.UnidadeProducaoService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("it-postgres")
class CanonicalProductionRoutingIT extends PostgresTestcontainersConfig {

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired UnidadeProducaoService unidadeProducaoService;
    @Autowired RotaProducaoService rotaProducaoService;

    @Test
    void routesSameTenantCategoryPerInstitutionAndRejectsCrossTenant() {
        Tenant tenantA = tenant("Routing A");
        Tenant tenantB = tenant("Routing B");
        Instituicao institutionA1 = institution(tenantA, "A1");
        Instituicao institutionA2 = institution(tenantA, "A2");
        Instituicao institutionB = institution(tenantB, "B1");
        UnidadeAtendimento serviceA1 = serviceUnit(institutionA1, "Service A1");
        UnidadeAtendimento serviceA2 = serviceUnit(institutionA2, "Service A2");
        UnidadeAtendimento serviceB = serviceUnit(institutionB, "Service B");
        CategoriaProduto snacksA = category(tenantA, "Lanches");

        UnidadeProducao kitchenA1 = unidadeProducaoService.criar(
                tenantA.getId(), institutionA1.getId(), serviceA1.getId(),
                "Cozinha A1", "COZINHA_A1", UnidadeProducaoTipo.COZINHA, 0);
        UnidadeProducao kitchenA2 = unidadeProducaoService.criar(
                tenantA.getId(), institutionA2.getId(), serviceA2.getId(),
                "Cozinha A2", "COZINHA_A2", UnidadeProducaoTipo.COZINHA, 0);
        UnidadeProducao kitchenB = unidadeProducaoService.criar(
                tenantB.getId(), institutionB.getId(), serviceB.getId(),
                "Cozinha B", "COZINHA_B", UnidadeProducaoTipo.COZINHA, 0);

        var routeA1 = rotaProducaoService.configurarRota(
                tenantA.getId(), snacksA.getId(), kitchenA1.getId(), 0);
        var routeA2 = rotaProducaoService.configurarRota(
                tenantA.getId(), snacksA.getId(), kitchenA2.getId(), 0);

        assertThat(routeA1.getInstituicao().getId()).isEqualTo(institutionA1.getId());
        assertThat(routeA2.getInstituicao().getId()).isEqualTo(institutionA2.getId());
        assertThat(rotaProducaoService.resolverUnidadeProducaoParaCategoria(
                tenantA.getId(), institutionA1.getId(), snacksA.getId()).getId())
                .isEqualTo(kitchenA1.getId());
        assertThat(rotaProducaoService.resolverUnidadeProducaoParaCategoria(
                tenantA.getId(), institutionA2.getId(), snacksA.getId()).getId())
                .isEqualTo(kitchenA2.getId());

        assertThatThrownBy(() -> rotaProducaoService.configurarRota(
                tenantA.getId(), snacksA.getId(), kitchenB.getId(), 0))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> unidadeProducaoService.desativar(tenantA.getId(), kitchenA1.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("rotas activas");

        rotaProducaoService.desativarRota(tenantA.getId(), routeA1.getId());
        assertThat(unidadeProducaoService.desativar(tenantA.getId(), kitchenA1.getId()).getAtivo())
                .isFalse();
    }

    private Tenant tenant(String name) {
        String suffix = suffix();
        Tenant tenant = new Tenant();
        tenant.setNome(name);
        tenant.setSlug("routing-" + suffix);
        tenant.setTenantCode("RT" + suffix.substring(0, 8).toUpperCase());
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(tenant);
    }

    private Instituicao institution(Tenant tenant, String name) {
        String suffix = suffix();
        Instituicao institution = new Instituicao();
        institution.setTenant(tenant);
        institution.setNome(name);
        institution.setSigla(("I" + suffix).substring(0, 9).toUpperCase());
        institution.setNif("NIF-" + suffix);
        institution.setTelefoneAutorizacao("+2449" + suffix.substring(0, 8));
        institution.setAtiva(true);
        return instituicaoRepository.saveAndFlush(institution);
    }

    private UnidadeAtendimento serviceUnit(Instituicao institution, String name) {
        UnidadeAtendimento unit = new UnidadeAtendimento();
        unit.setInstituicao(institution);
        unit.setNome(name);
        unit.setTipo(TipoUnidadeAtendimento.RESTAURANTE);
        unit.setAtiva(true);
        return unidadeAtendimentoRepository.saveAndFlush(unit);
    }

    private CategoriaProduto category(Tenant tenant, String name) {
        CategoriaProduto category = new CategoriaProduto();
        category.setTenant(tenant);
        category.setNome(name);
        category.setSlug("category-" + suffix());
        category.setOrdem(0);
        category.setAtivo(true);
        return categoriaProdutoRepository.saveAndFlush(category);
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
