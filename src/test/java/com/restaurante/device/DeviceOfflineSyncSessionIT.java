package com.restaurante.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.DeviceOfflineCommandRequest;
import com.restaurante.dto.request.DeviceOfflineSyncBatchRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.OperationalDeviceType;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.device.offline.repository.DeviceOfflineCommandRepository;
import com.restaurante.device.offline.repository.DeviceOfflineSyncSessionRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.DeviceAuthIntegrationTestSupport;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.transaction.annotation.Transactional;
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class DeviceOfflineSyncSessionIT extends DeviceAuthIntegrationTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired DeviceOfflineSyncSessionRepository syncSessionRepository;
    @Autowired DeviceOfflineCommandRepository commandRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void batch_creates_sync_session_and_associates_commands() throws Exception {
        final ProvisionarTenantResponse[] provArr = new ProvisionarTenantResponse[1];
        final Produto[] prodArr = new Produto[1];
        final DispositivoOperacional[] dispArr = new DispositivoOperacional[1];

        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            provArr[0] = provisionTenant("offline-sess-obs", "OSO");
            prodArr[0] = criarProdutoBasico(provArr[0].getTenantId());
            dispArr[0] = criarDevicePos(provArr[0], OperationalDeviceType.POS_CAIXA);
        });

        ProvisionarTenantResponse prov = provArr[0];
        Produto prod = prodArr[0];
        DispositivoOperacional disp = dispArr[0];
        String deviceToken = activateDeviceForTest(disp, List.of(
                DeviceCapability.OFFLINE_SYNC,
                DeviceCapability.OFFLINE_CREATE_ORDER,
                DeviceCapability.CREATE_ORDER
        ));

        TenantContextHolder.clear();
        String token = issueTenantOwnerToken(prov);
        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(abrirTurnoReq(prov))))
                .andExpect(status().isCreated());

        DeviceOfflineSyncBatchRequest batch = new DeviceOfflineSyncBatchRequest();
        batch.setSyncSessionId("sess-1");
        batch.setAppVersion("1.0.0");
        batch.setDeviceLocalTime(Instant.now());
        batch.setOfflineStartedAt(Instant.now().minusSeconds(120));
        batch.setOfflineEndedAt(Instant.now());

        DeviceOfflineCommandRequest cmd = new DeviceOfflineCommandRequest();
        cmd.setClientRequestId("cmd-1");
        cmd.setCommandType(DeviceOfflineCommandType.CREATE_PEDIDO_POS);
        cmd.setCommandVersion("1");
        cmd.setLocalCreatedAt(Instant.now());
        cmd.setPayload(objectMapper.readTree("""
                {"itens":[{"produtoId": %d, "quantidade": 1}], "localTotalEstimado": 10.00}
                """.formatted(prod.getId())));
        batch.setCommands(List.of(cmd));

        String resp = mockMvc.perform(post("/device/offline-sync/batch")
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(resp).at("/data");

        String serverSyncId = json.get("serverSyncId").asText();
        assertThat(serverSyncId).isNotBlank();
        assertThat(json.get("syncSessionId").asText()).isEqualTo("sess-1");
        assertThat(json.get("syncSessionStatus").asText()).isEqualTo("COMPLETED");

        var session = syncSessionRepository.findByTenantIdAndServerSyncId(prov.getTenantId(), serverSyncId).orElseThrow();
        assertThat(session.getAppVersion()).isEqualTo("1.0.0");
        assertThat(session.getTotalCommands()).isEqualTo(1);
        assertThat(session.getAppliedCount()).isEqualTo(1);
        assertThat(session.getStatus().name()).isEqualTo("COMPLETED");

        var savedCmd = commandRepository.findByTenantIdAndDispositivoOperacionalIdAndClientRequestId(prov.getTenantId(), disp.getId(), "cmd-1").orElseThrow();
        assertThat(savedCmd.getServerSyncId()).isEqualTo(serverSyncId);
        assertThat(savedCmd.getSyncSession()).isNotNull();
        assertThat(savedCmd.getSyncSession().getId()).isEqualTo(session.getId());
    }

    private Produto criarProdutoBasico(Long tenantId) {
        Tenant t = tenantRepository.findById(tenantId).orElseThrow();
        CategoriaProduto cat = new CategoriaProduto();
        cat.setTenant(t);
        cat.setNome("Geral");
        cat.setSlug("geral-" + (System.nanoTime() % 100_000));
        cat.setAtivo(true);
        cat = categoriaProdutoRepository.save(cat);

        Produto prod = Produto.builder()
                .codigo("P-" + (System.nanoTime() % 1_000_000))
                .nome("Produto Offline")
                .preco(new BigDecimal("10.00"))
                .categoria(CategoriaProdutoLegacy.OUTROS)
                .ativo(true)
                .disponivel(true)
                .build();
        prod.setTenant(t);
        prod.setCategoriaProduto(cat);
        return produtoRepository.save(prod);
    }

    private DispositivoOperacional criarDevicePos(ProvisionarTenantResponse prov, OperationalDeviceType opType) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        var inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        var ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setCodigo("DEV-OFF-" + System.nanoTime());
        d.setNome("POS Offline");
        d.setTipo(DispositivoTipo.POS);
        d.setOperationalDeviceType(opType);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }

    private ProvisionarTenantResponse provisionTenant(String nome, String code) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false
        ));
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 100_000L));
        String uniqueCode = code + suffix;
        if (uniqueCode.length() > 10) uniqueCode = uniqueCode.substring(0, 10);
        String phone = "+244900" + Math.abs(nome.hashCode() % 1_000_000);
        var prov = provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + nome)
                                .slug(nome + "-" + suffix)
                                .tenantCode(uniqueCode)
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + nome)
                                .sigla(uniqueCode.substring(0, Math.min(4, uniqueCode.length())))
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(nome + suffix + "@owner.com")
                                .telefone(phone)
                                .criarUsuario(true)
                                .build())
                        .build()
        );

        // Provisionar Cozinha CENTRAL ativa para a unidade
        var ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();
        Cozinha cozinha = Cozinha.builder()
                .nome("Cozinha CENTRAL")
                .tipo(TipoCozinha.CENTRAL)
                .ativa(true)
                .descricao("Cozinha Central Offline")
                .build();
        cozinha = cozinhaRepository.saveAndFlush(cozinha);
        ua.adicionarCozinha(cozinha);
        unidadeAtendimentoRepository.saveAndFlush(ua);

        return prov;
    }

    private AbrirTurnoRequest abrirTurnoReq(ProvisionarTenantResponse prov) {
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno POS");

        ChecklistItemRespostaRequest it1 = new ChecklistItemRespostaRequest();
        it1.setCodigo("DEVICE_ONLINE");
        it1.setValorBoolean(true);

        ChecklistItemRespostaRequest it2 = new ChecklistItemRespostaRequest();
        it2.setCodigo("QR_VISIVEL");
        it2.setValorBoolean(true);

        ChecklistItemRespostaRequest it3 = new ChecklistItemRespostaRequest();
        it3.setCodigo("CATALOGO_ATUALIZADO");
        it3.setValorBoolean(true);

        ChecklistItemRespostaRequest it4 = new ChecklistItemRespostaRequest();
        it4.setCodigo("UNIDADE_PRODUCAO_ATIVA");
        it4.setValorBoolean(true);

        ChecklistItemRespostaRequest it5 = new ChecklistItemRespostaRequest();
        it5.setCodigo("OPERADOR_CONFIRMOU");
        it5.setValorBoolean(true);

        req.setChecklist(List.of(it1, it2, it3, it4, it5));
        return req;
    }
}
