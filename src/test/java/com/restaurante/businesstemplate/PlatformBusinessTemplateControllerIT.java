package com.restaurante.businesstemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.ChecklistOperacionalItemTemplateRepository;
import com.restaurante.repository.ChecklistOperacionalTemplateRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.PlanoRepository;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.RotaProducaoCategoriaRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantOperacaoPolicyRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UnidadeProducaoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class PlatformBusinessTemplateControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired TenantRepository tenantRepository;
    @Autowired SubscricaoRepository subscricaoRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired QrCodeOperacionalRepository qrCodeOperacionalRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired MesaRepository mesaRepository;
    @Autowired UnidadeProducaoRepository unidadeProducaoRepository;
    @Autowired RotaProducaoCategoriaRepository rotaProducaoCategoriaRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired ChecklistOperacionalTemplateRepository checklistOperacionalTemplateRepository;
    @Autowired ChecklistOperacionalItemTemplateRepository checklistOperacionalItemTemplateRepository;
    @Autowired TenantOperacaoPolicyRepository tenantOperacaoPolicyRepository;
    @Autowired PlanoRepository planoRepository;

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "platform-admin")
    void previewPonto_doesNotPersist() throws Exception {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        long beforeTenants = tenantRepository.count();
        String slug = UniqueTestData.uniqueSlug("banca-tia-rosa-preview");
        String tenantPhone = UniqueTestData.uniqueTelefone();
        String ownerPhone = UniqueTestData.uniqueTelefone();
        String email = UniqueTestData.uniqueEmail("rosa-preview");

        String payload = """
                {
                  "planoCodigo": "PILOTO",
                  "tenant": {
                    "nomeNegocio": "Banca da Tia Rosa",
                    "slug": "%s",
                    "tipo": "VENDEDOR_RUA",
                    "telefone": "%s",
                    "email": "%s"
                  },
                  "owner": {
                    "nome": "Rosa Manuel",
                    "telefone": "%s",
                    "email": "%s"
                  },
                  "ponto": {
                    "entregaManual": true,
                    "allowPickup": true
                  }
                }
                """.formatted(slug, tenantPhone, email, ownerPhone, email);

        mockMvc.perform(post("/platform/templates/CONSUMA_PONTO_V1/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(tenantRepository.count()).isEqualTo(beforeTenants);
        assertThat(tenantRepository.findBySlug(slug)).isEmpty();
    }

    @Test
    @WithMockUser(username = "platform-admin")
    void previewRest_doesNotPersist() throws Exception {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        long beforeTenants = tenantRepository.count();
        String slug = UniqueTestData.uniqueSlug("rest-kialo-preview");
        String tenantPhone = UniqueTestData.uniqueTelefone();
        String ownerPhone = UniqueTestData.uniqueTelefone();
        String email = UniqueTestData.uniqueEmail("kialo-preview");

        String payload = """
                {
                  "planoCodigo": "PILOTO",
                  "tenant": {
                    "nomeNegocio": "Restaurante Kialo",
                    "slug": "%s",
                    "tipo": "RESTAURANTE",
                    "telefone": "%s",
                    "email": "%s"
                  },
                  "owner": {
                    "nome": "Owner Kialo",
                    "telefone": "%s",
                    "email": "%s"
                  },
                  "rest": {
                    "temMesas": true,
                    "quantidadeMesas": 10,
                    "gerarQrPorMesa": true,
                    "temBarSeparado": true,
                    "exigeTurnoAberto": true,
                    "entrega": "MANUAL"
                  }
                }
                """.formatted(slug, tenantPhone, email, ownerPhone, email);

        mockMvc.perform(post("/platform/templates/CONSUMA_REST_V1/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(tenantRepository.count()).isEqualTo(beforeTenants);
        assertThat(tenantRepository.findBySlug(slug)).isEmpty();
    }

    @Test
    @WithMockUser(username = "platform-admin")
    void provisionPonto_createsMinimalOperationalStructure_andStaysSimple() throws Exception {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));
        String slug = UniqueTestData.uniqueSlug("banca-tia-rosa");
        String tenantCode = UniqueTestData.uniqueTenantCode("ROSA");
        String tenantPhone = UniqueTestData.uniqueTelefone();
        String ownerPhone = UniqueTestData.uniqueTelefone();
        String ownerEmail = UniqueTestData.uniqueEmail("rosa");

        String payload = """
                {
                  "planoCodigo": "PILOTO",
                  "tenant": {
                    "nomeNegocio": "Banca da Tia Rosa",
                    "slug": "%s",
                    "tenantCode": "%s",
                    "tipo": "VENDEDOR_RUA",
                    "telefone": "%s",
                    "email": "%s"
                  },
                  "owner": {
                    "nome": "Rosa Manuel",
                    "telefone": "%s",
                    "email": "%s",
                    "senhaTemporaria": "Alterar@123"
                  },
                  "ponto": {
                    "entregaManual": false,
                    "allowPickup": true
                  }
                }
                """.formatted(slug, tenantCode, tenantPhone, ownerEmail, ownerPhone, ownerEmail);

        String resp = mockMvc.perform(post("/platform/templates/CONSUMA_PONTO_V1/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        long tenantId = json.at("/data/tenantId").asLong();

        Tenant t = tenantRepository.findById(tenantId).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(TenantEstado.ATIVO);
        assertThat(t.getTemplateCode()).isEqualTo("CONSUMA_PONTO");
        assertThat(t.getTemplateVersion()).isEqualTo(1);
        assertThat(t.getProvisionedAt()).isNotNull();
        assertThat(t.getProvisioningSource()).isEqualTo("PLATFORM_TEMPLATE_API");

        assertThat(subscricaoRepository.findByTenantId(t.getId())).isNotEmpty();
        assertThat(instituicaoRepository.findByTenantId(t.getId())).hasSize(1);
        assertThat(unidadeAtendimentoRepository.countByTenantId(t.getId())).isEqualTo(1);

        assertThat(categoriaProdutoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(t.getId()))
                .extracting("slug")
                .contains("bebidas", "lanches", "produtos-rapidos", "servicos", "outros");

        var owner = userRepository.findByEmail(ownerEmail).orElseThrow();
        assertThat(tenantUserRepository.findAllByTenantIdAndUserId(t.getId(), owner.getId())).isNotEmpty();
        assertThat(qrCodeOperacionalRepository.countByTenantId(t.getId())).isEqualTo(1);

        // PONTO stays simple: no mesas, no produção, no devices.
        assertThat(mesaRepository.findByTenantId(t.getId())).isEmpty();
        assertThat(unidadeProducaoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(t.getId())).isEmpty();
        assertThat(dispositivoOperacionalRepository.countByTenantIdAndStatusNot(t.getId(), DispositivoStatus.REVOGADO)).isEqualTo(0);

        var op = tenantOperacaoPolicyRepository.findByTenantId(t.getId()).orElseThrow();
        assertThat(op.isRequireOpenTurnoForOrders()).isFalse();
        assertThat(op.isProductionEnabled()).isFalse();
        assertThat(op.isPosEnabled()).isFalse();
        assertThat(op.isKdsEnabled()).isFalse();
    }

    @Test
    @WithMockUser(username = "platform-admin")
    void provisionRest_with10Tables_createsQrsProductionDevicesChecklists() throws Exception {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));
        String slug = UniqueTestData.uniqueSlug("rest-kialo");
        String tenantCode = UniqueTestData.uniqueTenantCode("KIALO");
        String tenantPhone = UniqueTestData.uniqueTelefone();
        String ownerPhone = UniqueTestData.uniqueTelefone();
        String ownerEmail = UniqueTestData.uniqueEmail("kialo");

        String payload = """
                {
                  "planoCodigo": "PILOTO",
                  "tenant": {
                    "nomeNegocio": "Restaurante Kialo",
                    "slug": "%s",
                    "tenantCode": "%s",
                    "tipo": "RESTAURANTE",
                    "telefone": "%s",
                    "email": "%s"
                  },
                  "owner": {
                    "nome": "Owner Kialo",
                    "telefone": "%s",
                    "email": "%s"
                  },
                  "rest": {
                    "temMesas": true,
                    "quantidadeMesas": 10,
                    "gerarQrPorMesa": true,
                    "temBarSeparado": true,
                    "exigeTurnoAberto": true,
                    "entrega": "NONE"
                  }
                }
                """.formatted(slug, tenantCode, tenantPhone, ownerEmail, ownerPhone, ownerEmail);

        String resp = mockMvc.perform(post("/platform/templates/CONSUMA_REST_V1/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        long tenantId = json.at("/data/tenantId").asLong();

        Tenant t = tenantRepository.findById(tenantId).orElseThrow();
        assertThat(t.getTemplateCode()).isEqualTo("CONSUMA_REST");
        assertThat(t.getTemplateVersion()).isEqualTo(1);

        assertThat(categoriaProdutoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(t.getId())).hasSize(4);
        assertThat(mesaRepository.findByTenantId(t.getId())).hasSize(10);

        // QR principal + 10 QRs por mesa
        assertThat(qrCodeOperacionalRepository.countByTenantId(t.getId())).isEqualTo(11);

        // produção + rotas
        assertThat(unidadeProducaoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(t.getId()).size()).isGreaterThanOrEqualTo(2);
        assertThat(rotaProducaoCategoriaRepository.findByTenantId(t.getId())).hasSize(4);

        // devices: POS + KDS cozinha
        assertThat(dispositivoOperacionalRepository.countByTenantIdAndStatusNot(t.getId(), DispositivoStatus.REVOGADO)).isEqualTo(2);

        // checklists: abertura + fecho
        assertThat(checklistOperacionalTemplateRepository.findByTenantIdAndTipoAndAtivoTrueOrderByIdAsc(t.getId(), com.restaurante.model.enums.ChecklistTipo.ABERTURA)).isNotEmpty();
        assertThat(checklistOperacionalTemplateRepository.findByTenantIdAndTipoAndAtivoTrueOrderByIdAsc(t.getId(), com.restaurante.model.enums.ChecklistTipo.FECHO)).isNotEmpty();

        var op = tenantOperacaoPolicyRepository.findByTenantId(t.getId()).orElseThrow();
        assertThat(op.isRequireOpenTurnoForOrders()).isTrue();
        assertThat(op.isProductionEnabled()).isTrue();
        assertThat(op.isPosEnabled()).isTrue();
        assertThat(op.isKdsEnabled()).isTrue();
        assertThat(op.isAllowTableQr()).isTrue();
    }

    @Test
    @WithMockUser(username = "platform-admin")
    void slugDuplicado_returnsError() throws Exception {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        Tenant existing = new Tenant();
        existing.setNome("Existing");
        String duplicatedSlug = UniqueTestData.uniqueSlug("slug-dup");
        existing.setSlug(duplicatedSlug);
        existing.setTenantCode(UniqueTestData.uniqueTenantCode("DUP01"));
        existing.setTipo(TenantTipo.VENDEDOR_RUA);
        existing.setEstado(TenantEstado.ATIVO);
        tenantRepository.saveAndFlush(existing);
        String ownerPhone = UniqueTestData.uniqueTelefone();

        String payload = """
                {
                  "planoCodigo": "PILOTO",
                  "tenant": { "nomeNegocio": "X", "slug": "%s", "tipo": "VENDEDOR_RUA" },
                  "owner": { "nome": "O", "telefone": "%s" }
                }
                """.formatted(duplicatedSlug, ownerPhone);

        mockMvc.perform(post("/platform/templates/CONSUMA_PONTO_V1/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "platform-admin")
    void planLimit_blocksProvisioning() throws Exception {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        Plano p = new Plano();
        String planCode = UniqueTestData.uniqueTenantCode("LIMIT0");
        p.setCodigo(planCode);
        p.setNome("Limit 0");
        p.setDescricao("Blocks institutions");
        p.setPrecoMensal(BigDecimal.ZERO);
        p.setMaxInstituicoes(0);
        p.setMaxUnidadesAtendimento(0);
        p.setMaxProdutos(0);
        p.setMaxUsuarios(0);
        p.setMaxQrCodes(0);
        p.setMaxDispositivos(0);
        p.setPermiteMultiInstituicao(false);
        p.setPermitePedidosQr(true);
        p.setPermitePos(true);
        p.setPermiteOffline(false);
        p.setAtivo(true);
        planoRepository.saveAndFlush(p);

        String slug = UniqueTestData.uniqueSlug("limit-tenant");
        String tenantCode = UniqueTestData.uniqueTenantCode("LIMT");
        String ownerPhone = UniqueTestData.uniqueTelefone();
        String payload = """
                {
                  "planoCodigo": "%s",
                  "tenant": { "nomeNegocio": "Limit", "slug": "%s", "tipo": "VENDEDOR_RUA", "tenantCode": "%s" },
                  "owner": { "nome": "O", "telefone": "%s" }
                }
                """.formatted(planCode, slug, tenantCode, ownerPhone);

        mockMvc.perform(post("/platform/templates/CONSUMA_PONTO_V1/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());

        assertThat(tenantRepository.findBySlug(slug)).isEmpty();
    }

    @Test
    @WithMockUser(username = "platform-admin")
    void failureMidway_rollsBack() throws Exception {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        // Cria uma instituição pré-existente com NIF global para forçar UNIQUE violation no meio.
        Tenant t0 = new Tenant();
        t0.setNome("T0");
        t0.setSlug(UniqueTestData.uniqueSlug("t0-bt"));
        t0.setTenantCode(UniqueTestData.uniqueTenantCode("T0BT"));
        t0.setTipo(TenantTipo.RESTAURANTE);
        t0.setEstado(TenantEstado.ATIVO);
        t0 = tenantRepository.saveAndFlush(t0);

        // subscrição ativa para satisfazer FKs/consistência em repo (não é estritamente necessário para criar instituicao)
        var piloto = planoRepository.findByCodigo("PILOTO").orElseThrow();
        var s0 = new com.restaurante.model.entity.Subscricao();
        s0.setTenant(t0);
        s0.setPlano(piloto);
        s0.setEstado(com.restaurante.model.enums.SubscricaoEstado.ATIVA);
        s0.setInicioEm(java.time.LocalDate.now());
        s0.setRenovacaoAutomatica(false);
        subscricaoRepository.saveAndFlush(s0);

        var inst0 = new com.restaurante.model.entity.Instituicao();
        inst0.setTenant(t0);
        inst0.setNome("Existing Inst");
        String conflictNif = UniqueTestData.uniqueNif("CONFLITO");
        inst0.setSigla(UniqueTestData.uniqueInstituicaoSigla("EXBT"));
        inst0.setNif(conflictNif);
        inst0.setTelefoneAutorizacao(UniqueTestData.uniqueTelefone());
        inst0.setAtiva(true);
        instituicaoRepository.saveAndFlush(inst0);
        String rollbackSlug = UniqueTestData.uniqueSlug("bt-rollback");
        String rollbackTenantCode = UniqueTestData.uniqueTenantCode("RBKT");
        String rollbackOwnerPhone = UniqueTestData.uniqueTelefone();

        String payload = """
                {
                  "planoCodigo": "PILOTO",
                  "tenant": {
                    "nomeNegocio": "Rollback",
                    "slug": "%s",
                    "tenantCode": "%s",
                    "tipo": "VENDEDOR_RUA",
                    "nif": "%s"
                  },
                  "owner": { "nome": "O", "telefone": "%s" }
                }
                """.formatted(rollbackSlug, rollbackTenantCode, conflictNif, rollbackOwnerPhone);

        mockMvc.perform(post("/platform/templates/CONSUMA_PONTO_V1/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is5xxServerError());

        assertThat(tenantRepository.findBySlug(rollbackSlug)).isEmpty();
        assertThat(tenantRepository.findByTenantCode(rollbackTenantCode)).isEmpty();
    }

    @Test
    @WithMockUser(username = "non-platform")
    void nonPlatformAdmin_cannotProvision() throws Exception {
        TenantContextHolder.set(new TenantContext(
                1L, "TA", 2L, Set.of("ROLE_GERENTE"),
                TenantResolutionSource.JWT, false, false
        ));

        String payload = """
                {
                  "planoCodigo": "PILOTO",
                  "tenant": { "nomeNegocio": "X", "slug": "%s", "tipo": "VENDEDOR_RUA" },
                  "owner": { "nome": "O", "telefone": "%s" }
                }
                """.formatted(UniqueTestData.uniqueSlug("x-bt"), UniqueTestData.uniqueTelefone());

        mockMvc.perform(post("/platform/templates/CONSUMA_PONTO_V1/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }
}
