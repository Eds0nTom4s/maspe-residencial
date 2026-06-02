package com.restaurante.operacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.PublicQrPedidoItemRequest;
import com.restaurante.dto.request.PublicQrPedidoRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.restaurante.testsupport.UniqueTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.web-application-type=servlet",
                "consuma.operacao.require-open-turno-for-orders=true"
        }
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class TurnoOperacionalRequireOpenForOrdersIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired TenantRepository tenantRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "public")
    void public_qr_order_is_blocked_when_turno_required_and_none_open() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("op-turno-req", "OTR");
        Produto prod = createProdutoMinimo(prov.getTenantId(), "turno-required");

        PublicQrPedidoRequest qrReq = new PublicQrPedidoRequest();
        PublicQrPedidoItemRequest it = new PublicQrPedidoItemRequest();
        it.setProdutoId(prod.getId());
        it.setQuantidade(1);
        qrReq.setItens(List.of(it));
        qrReq.setIdempotencyKey("idem-req");

        mockMvc.perform(post("/public/q/" + prov.getQrToken() + "/pedidos")
                        .header("Idempotency-Key", "idem-req")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(qrReq)))
                .andExpect(status().isConflict());
    }

    private ProvisionarTenantResponse provisionTenant(String slug, String tenantCode) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false
        ));
        String uniqueSlug = UniqueTestData.uniqueSlug(slug);
        String uniqueTenantCode = UniqueTestData.uniqueTenantCode(tenantCode);
        String uniqueSigla = UniqueTestData.uniqueInstituicaoSigla(tenantCode);
        String ownerEmail = UniqueTestData.uniqueEmail(slug + "-owner");
        String phone = UniqueTestData.uniqueTelefone();
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + slug)
                                .slug(uniqueSlug)
                                .tenantCode(uniqueTenantCode)
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + slug)
                                .sigla(uniqueSigla)
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(ownerEmail)
                                .telefone(phone)
                                .criarUsuario(true)
                                .build())
                        .build()
        );
    }

    private Produto createProdutoMinimo(Long tenantId, String suffix) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        CategoriaProduto cat = categoriaProdutoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(tenantId)
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    CategoriaProduto created = new CategoriaProduto();
                    created.setTenant(tenant);
                    created.setNome("Auto");
                    created.setSlug(UniqueTestData.uniqueSlug("auto-cat"));
                    created.setDescricao("Categoria criada automaticamente para testes IT");
                    created.setOrdem(0);
                    created.setAtivo(true);
                    return categoriaProdutoRepository.saveAndFlush(created);
                });

        Produto p = new Produto();
        p.setTenant(tenant);
        p.setCodigo(UniqueTestData.uniqueTenantCode("P" + suffix));
        p.setNome("Produto IT " + suffix);
        p.setDescricao("Fixture IT");
        p.setPreco(new BigDecimal("10.00"));
        p.setCategoria(CategoriaProdutoLegacy.OUTROS);
        p.setCategoriaProduto(cat);
        p.setDisponivel(true);
        p.setAtivo(true);
        return produtoRepository.saveAndFlush(p);
    }
}
