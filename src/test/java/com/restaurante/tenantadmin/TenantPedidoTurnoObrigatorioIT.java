package com.restaurante.tenantadmin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TipoPagamentoPedido;
import com.restaurante.model.enums.TipoSessao;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.web-application-type=servlet",
                "consuma.operacao.turno-obrigatorio=true"
        }
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
@org.springframework.transaction.annotation.Transactional
class TenantPedidoTurnoObrigatorioIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired UserRepository userRepository;
    @Autowired TurnoOperacionalRepository turnoOperacionalRepository;
    @Autowired SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired PedidoRepository pedidoRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void pedidos_requiresOpenTurnoAndListsOnlyCurrentTurno() throws Exception {
        ProvisionarTenantResponse provisioned = provisionTenant();
        setTenantContext(provisioned);

        mockMvc.perform(get("/tenant/operacao/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.turnoObrigatorio").value(true))
                .andExpect(jsonPath("$.data.pedidosEscopo").value("TURNO_ATUAL"));

        mockMvc.perform(get("/tenant/pedidos")
                        .param("instituicaoId", provisioned.getInstituicaoId().toString())
                        .param("unidadeAtendimentoId", provisioned.getUnidadeAtendimentoId().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TURNO_ABERTO_OBRIGATORIO"));

        Tenant tenant = tenantRepository.findById(provisioned.getTenantId()).orElseThrow();
        Instituicao instituicao = instituicaoRepository.findById(provisioned.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento unidade = unidadeAtendimentoRepository.findById(provisioned.getUnidadeAtendimentoId()).orElseThrow();
        User owner = userRepository.findById(provisioned.getOwnerUserId()).orElseThrow();

        TurnoOperacional turnoFechado = criarTurno(tenant, instituicao, unidade, owner, TurnoOperacionalStatus.FECHADO);
        TurnoOperacional turnoAberto = criarTurno(tenant, instituicao, unidade, owner, TurnoOperacionalStatus.ABERTO);

        Pedido pedidoFechado = criarPedido(tenant, instituicao, unidade, turnoFechado, "FECHADO");
        Pedido pedidoAberto = criarPedido(tenant, instituicao, unidade, turnoAberto, "ABERTO");

        String response = mockMvc.perform(get("/tenant/pedidos")
                        .param("instituicaoId", instituicao.getId().toString())
                        .param("unidadeAtendimentoId", unidade.getId().toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode content = objectMapper.readTree(response).at("/data/content");
        assertThat(content).hasSize(1);
        assertThat(content.toString()).contains(pedidoAberto.getNumero());
        assertThat(content.toString()).doesNotContain(pedidoFechado.getNumero());
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void aceitarPedido_requiresOpenTurnoWhenTurnoObrigatorio() throws Exception {
        ProvisionarTenantResponse provisioned = provisionTenant();
        setTenantContext(provisioned);

        Tenant tenant = tenantRepository.findById(provisioned.getTenantId()).orElseThrow();
        Instituicao instituicao = instituicaoRepository.findById(provisioned.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento unidade = unidadeAtendimentoRepository.findById(provisioned.getUnidadeAtendimentoId()).orElseThrow();
        Pedido pedidoSemTurno = criarPedido(tenant, instituicao, unidade, null, "SEM-TURNO");

        mockMvc.perform(patch("/tenant/pedidos/" + pedidoSemTurno.getId() + "/aceitar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TURNO_ABERTO_OBRIGATORIO"));
    }

    private ProvisionarTenantResponse provisionTenant() {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false
        ));

        String slug = UniqueTestData.uniqueSlug("pedidos-turno");
        String code = UniqueTestData.uniqueTenantCode("PT");
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Pedidos Turno")
                                .slug(slug)
                                .tenantCode(code)
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Instituição Pedidos Turno")
                                .sigla(UniqueTestData.uniqueInstituicaoSigla("PT"))
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(UniqueTestData.uniqueEmail("pedidos-turno"))
                                .telefone(UniqueTestData.uniqueTelefone())
                                .criarUsuario(true)
                                .build())
                        .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder()
                                .criarMesas(false)
                                .criarQrPorMesa(false)
                                .criarQrPrincipal(true)
                                .build())
                        .build()
        );
    }

    private void setTenantContext(ProvisionarTenantResponse provisioned) {
        TenantContextHolder.set(new TenantContext(
                provisioned.getTenantId(),
                provisioned.getTenantCode(),
                provisioned.getOwnerUserId(),
                Set.of(TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT,
                false,
                false
        ));
    }

    private TurnoOperacional criarTurno(Tenant tenant,
                                        Instituicao instituicao,
                                        UnidadeAtendimento unidade,
                                        User owner,
                                        TurnoOperacionalStatus status) {
        LocalDateTime now = LocalDateTime.now();
        TurnoOperacional turno = new TurnoOperacional();
        turno.setTenant(tenant);
        turno.setInstituicao(instituicao);
        turno.setUnidadeAtendimento(unidade);
        turno.setAbertoPor(owner);
        turno.setStatus(status);
        turno.setTipo(TurnoOperacionalTipo.DIARIO);
        turno.setNome("Turno " + status.name());
        turno.setAbertoEm(now.minusHours(status == TurnoOperacionalStatus.ABERTO ? 1 : 5));
        if (status == TurnoOperacionalStatus.FECHADO) {
            turno.setFechadoPor(owner);
            turno.setFechadoEm(now.minusHours(2));
        }
        return turnoOperacionalRepository.saveAndFlush(turno);
    }

    private Pedido criarPedido(Tenant tenant,
                               Instituicao instituicao,
                               UnidadeAtendimento unidade,
                               TurnoOperacional turno,
                               String prefixo) {
        SessaoConsumo sessao = SessaoConsumo.builder()
                .qrCodeSessao(UUID.randomUUID().toString())
                .tenant(tenant)
                .instituicao(instituicao)
                .unidadeAtendimento(unidade)
                .mesa(null)
                .modoAnonimo(true)
                .status(StatusSessaoConsumo.ABERTA)
                .tipoSessao(TipoSessao.POS_PAGO)
                .build();
        sessao = sessaoConsumoRepository.saveAndFlush(sessao);

        Pedido pedido = new Pedido();
        pedido.setTenant(tenant);
        pedido.setNumero("TP-" + prefixo + "-" + UUID.randomUUID());
        pedido.setSessaoConsumo(sessao);
        pedido.setTurnoOperacional(turno);
        pedido.setStatus(StatusPedido.CRIADO);
        pedido.setStatusFinanceiro(StatusFinanceiroPedido.NAO_PAGO);
        pedido.setTipoPagamento(TipoPagamentoPedido.POS_PAGO);
        pedido.setTotal(new BigDecimal("25.00"));
        return pedidoRepository.saveAndFlush(pedido);
    }
}
