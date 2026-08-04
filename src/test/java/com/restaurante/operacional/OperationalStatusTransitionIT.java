package com.restaurante.operacional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ConfirmarPedidoPaymentOrderRequest;
import com.restaurante.fiscal.autoissue.event.PaymentConfirmedForFiscalIssueEvent;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.inventory.repository.InventoryConsumptionRecordRepository;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.InventoryConsumptionStatus;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.PedidoAllowedAction;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.PedidoService;
import com.restaurante.service.QrCodeOperacionalService;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
@RecordApplicationEvents
class OperationalStatusTransitionIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ApplicationEvents applicationEvents;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired PedidoService pedidoService;
    @Autowired SubPedidoRepository subPedidoRepository;
    @Autowired OrdemPagamentoRepository ordemPagamentoRepository;
    @Autowired PagamentoGatewayRepository pagamentoGatewayRepository;
    @Autowired OperationalEventLogRepository operationalEventLogRepository;
    @Autowired QrCodeOperacionalService qrCodeOperacionalService;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired TurnoOperacionalRepository turnoOperacionalRepository;
    @Autowired InventoryConsumptionRecordRepository inventoryConsumptionRecordRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "kitchen-user")
    void kitchen_canMoveSubPedidoToEmPreparacao_andPronto_andEventLogIsCreated() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-1", "OS1");
        pedidoService.confirmar(setup.pedidoId);

        // Actor kitchen no tenant
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), 1001L,
                Set.of(TenantUserRole.TENANT_KITCHEN.name()),
                TenantResolutionSource.JWT, false, false
        ));

        Long subPedidoId = setup.subPedidoId;

        mockMvc.perform(patch("/tenant/producao/subpedidos/" + subPedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_PREPARACAO\",\"motivo\":\"Inicio\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/tenant/producao/subpedidos/" + subPedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PRONTO\",\"motivo\":\"Finalizado\"}"))
                .andExpect(status().isOk());

        var events = operationalEventLogRepository.searchByTenantAndFilters(
                setup.tenant.getId(), setup.pedidoId, subPedidoId, OperationalEventType.SUBPEDIDO_STATUS_CHANGED,
                null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 50)
        );
        assertThat(events.getTotalElements()).isGreaterThanOrEqualTo(2);
        assertThat(events.getContent()).allMatch(e -> e.getStatusNovo() != null);
    }

    @Test
    @WithMockUser(username = "kitchen-user")
    void kitchen_cannotMarkEntregue() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-2", "OS2");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), 1002L,
                Set.of(TenantUserRole.TENANT_KITCHEN.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/producao/subpedidos/" + setup.subPedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENTREGUE\",\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "finance-user")
    void finance_cannotChangePedidoStatus_orViewOperationalEvents() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-3", "OS3");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), 1003L,
                Set.of(TenantUserRole.TENANT_FINANCE.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELADO\",\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/tenant/operacional/eventos?pedidoId=" + setup.pedidoId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "cashier-user")
    void cashier_canCancelPedido_onlyIfNotPaid_andEventLogCreated() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-4", "OS4");
        User cashier = criarTenantActor(setup.tenant, TenantUserRole.TENANT_CASHIER, "cashier-os4");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), cashier.getId(),
                Set.of(TenantUserRole.TENANT_CASHIER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELADO\",\"motivo\":\"Cliente desistiu\"}"))
                .andExpect(status().isOk());

        Pedido pedido = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(pedido.getStatus().name()).isEqualTo("CANCELADO");
        assertThat(pedido.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.NAO_PAGO);

        var events = operationalEventLogRepository.searchByTenantAndFilters(
                setup.tenant.getId(), setup.pedidoId, null, OperationalEventType.PEDIDO_STATUS_CHANGED,
                null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 20)
        );
        assertThat(events.getContent().stream().anyMatch(e -> "CANCELADO".equals(e.getStatusNovo()))).isTrue();
    }

    @Test
    @WithMockUser(username = "operator-user")
    void invalidTransitionReturns409_andFinancialIsNotChanged() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-5", "OS5");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), 1005L,
                Set.of(TenantUserRole.TENANT_OPERATOR.name()),
                TenantResolutionSource.JWT, false, false
        ));

        // PRONTO -> EM_PREPARACAO é inválido; primeiro faça PRONTO de forma direta deve falhar (PENDENTE -> PRONTO inválido)
        mockMvc.perform(patch("/tenant/producao/subpedidos/" + setup.subPedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PRONTO\",\"motivo\":\"pular\"}"))
                .andExpect(status().isConflict());

        Pedido pedido = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(pedido.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.NAO_PAGO);
    }

    @Test
    @WithMockUser(username = "operator-user")
    void operator_canFinalizePaidPedido_whenAllSubPedidosArePronto_andEventLogIsCreated() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-6", "OS6");
        pedidoService.confirmar(setup.pedidoId);

        // Primeiro: cozinha marca subpedido como EM_PREPARACAO -> PRONTO
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), 1006L,
                Set.of(TenantUserRole.TENANT_KITCHEN.name()),
                TenantResolutionSource.JWT, false, false
        ));
        mockMvc.perform(patch("/tenant/producao/subpedidos/" + setup.subPedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_PREPARACAO\",\"motivo\":\"Inicio\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/tenant/producao/subpedidos/" + setup.subPedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PRONTO\",\"motivo\":\"Finalizado\"}"))
                .andExpect(status().isOk());

        Pedido pedidoPago = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        pedidoPago.setStatusFinanceiro(StatusFinanceiroPedido.PAGO);
        pedidoRepository.saveAndFlush(pedidoPago);

        // Agora: operador finaliza pedido (FINALIZADO), entregando subpedidos PRONTO -> ENTREGUE
        User operator = criarTenantActor(setup.tenant, TenantUserRole.TENANT_OPERATOR, "operator-os6");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), operator.getId(),
                Set.of(TenantUserRole.TENANT_OPERATOR.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FINALIZADO\",\"motivo\":\"Entregue ao cliente\"}"))
                .andExpect(status().isOk());

        Pedido pedido = pedidoRepository.findByIdAndTenantIdComSessaoConsumo(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.FINALIZADO);
        assertThat(pedido.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.PAGO);
        assertThat(pedido.getSessaoConsumo()).isNotNull();
        assertThat(sessaoConsumoRepository.findById(pedido.getSessaoConsumo().getId()).orElseThrow().getStatus())
                .isEqualTo(StatusSessaoConsumo.ENCERRADA);

        var sub = subPedidoRepository.findByIdAndTenantId(setup.subPedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(sub.getStatus()).isEqualTo(StatusSubPedido.ENTREGUE);

        var events = operationalEventLogRepository.searchByTenantAndFilters(
                setup.tenant.getId(), setup.pedidoId, null, null,
                null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 50)
        );
        assertThat(events.getContent().stream().anyMatch(e -> e.getEventType() == OperationalEventType.SUBPEDIDO_STATUS_CHANGED && "ENTREGUE".equals(e.getStatusNovo()))).isTrue();
        assertThat(events.getContent().stream().anyMatch(e -> e.getEventType() == OperationalEventType.PEDIDO_STATUS_CHANGED && "FINALIZADO".equals(e.getStatusNovo()))).isTrue();
    }

    @Test
    @WithMockUser(username = "operator-user")
    void operator_canAcceptPedido_withoutChangingPaymentOrStartingProduction() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-7", "OS7");
        assertThat(ordemPagamentoRepository.findTopByTenantIdAndPedidoIdOrderByCreatedAtDesc(setup.tenant.getId(), setup.pedidoId))
                .isEmpty();
        User operator = criarTenantActor(setup.tenant, TenantUserRole.TENANT_OPERATOR, "operator-os7");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), operator.getId(),
                Set.of(TenantUserRole.TENANT_OPERATOR.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/aceitar"))
                .andExpect(status().isOk());

        Pedido pedido = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.EM_ANDAMENTO);
        assertThat(pedido.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.NAO_PAGO);
        assertThat(pagamentoGatewayRepository.findByPedidoIdOrderByCreatedAtDesc(setup.pedidoId)).isEmpty();

        var sub = subPedidoRepository.findByIdAndTenantId(setup.subPedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(sub.getStatus()).isEqualTo(StatusSubPedido.PENDENTE);

        OrdemPagamento ordem = ordemPagamentoRepository
                .findTopByTenantIdAndPedidoIdOrderByCreatedAtDesc(setup.tenant.getId(), setup.pedidoId)
                .orElseThrow();
        assertThat(ordem.getStatus()).isEqualTo(OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO);
        assertThat(ordem.getValor()).isEqualByComparingTo(pedido.getTotal());
        assertThat(ordem.getCreatedAt()).isNotNull();
        assertThat(ordem.getExpiresAt()).isAfter(ordem.getCreatedAt());

        var events = operationalEventLogRepository.searchByTenantAndFilters(
                setup.tenant.getId(), setup.pedidoId, null, null,
                null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 50)
        );
        assertThat(events.getContent().stream().anyMatch(e ->
                e.getEventType() == OperationalEventType.SUBPEDIDO_STATUS_CHANGED
                        && "PENDENTE".equals(e.getStatusNovo()))).isTrue();
        assertThat(events.getContent().stream().anyMatch(e ->
                e.getEventType() == OperationalEventType.PEDIDO_STATUS_CHANGED
                        && "EM_ANDAMENTO".equals(e.getStatusNovo()))).isTrue();
        assertThat(events.getContent().stream().anyMatch(e ->
                e.getEventType() == OperationalEventType.ORDEM_PAGAMENTO_CRIADA
                        && e.getEntityId().equals(ordem.getId()))).isTrue();
    }

    @Test
    @WithMockUser(username = "operator-user")
    void acceptPedido_isBlockedWhenAlreadyAccepted_andDoesNotChangePayment() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-8", "OS8");
        User operator = criarTenantActor(setup.tenant, TenantUserRole.TENANT_OPERATOR, "operator-os8");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), operator.getId(),
                Set.of(TenantUserRole.TENANT_OPERATOR.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/aceitar"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/aceitar"))
                .andExpect(status().isConflict());

        Pedido pedido = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.EM_ANDAMENTO);
        assertThat(pedido.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.NAO_PAGO);
        assertThat(ordemPagamentoRepository.findByTenantIdAndPedidoIdAndStatusOrderByCreatedAtDesc(
                setup.tenant.getId(),
                setup.pedidoId,
                OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO
        )).hasSize(1);
    }

    @Test
    @WithMockUser(username = "owner-user")
    void owner_canConfirmTpaWithoutOptionalReceiptReference_andPedidoStaysOperationallyOpen() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-pay-ok", "P12");
        User owner = criarTenantActor(setup.tenant, TenantUserRole.TENANT_OWNER, "owner-pay-ok");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), owner.getId(),
                Set.of(TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        abrirTurno(setup, owner);
        mockMvc.perform(post("/tenant/caixa-operador/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instituicaoId": %d,
                                  "unidadeAtendimentoId": %d
                                }
                                """.formatted(setup.instituicaoId, setup.unidadeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channel").value("WEB_PDV"));

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/aceitar"))
                .andExpect(status().isOk());

        String tenantDetail = mockMvc.perform(get("/tenant/pedidos/" + setup.pedidoId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode tenantData = objectMapper.readTree(tenantDetail).at("/data");
        assertThat(tenantData.at("/paymentOrder/status").asText()).isEqualTo("AGUARDANDO_CONFIRMACAO");
        assertThat(allowedActionsContain(tenantData, PedidoAllowedAction.CONFIRM_PAYMENT)).isTrue();

        String publicDetail = mockMvc.perform(get("/public/q/" + setup.qrToken + "/pedidos/" + setup.pedidoId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode publicData = objectMapper.readTree(publicDetail).at("/data");
        assertThat(publicData.at("/paymentOrder/status").asText()).isEqualTo("AGUARDANDO_CONFIRMACAO");
        assertThat(publicData.at("/paymentOrder/confirmedBy").isMissingNode()).isTrue();

        ConfirmarPedidoPaymentOrderRequest request = new ConfirmarPedidoPaymentOrderRequest();
        request.setClientRequestId("owner-confirm-payment-ok");
        request.setMetodoConfirmado(MetodoPagamentoManual.TPA);

        String confirmJson = mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/payment-order/confirm")
                        .header("Idempotency-Key", "owner-confirm-payment-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode confirmData = objectMapper.readTree(confirmJson).at("/data");
        assertThat(confirmData.at("/statusFinanceiro").asText()).isEqualTo("PAGO");
        assertThat(confirmData.at("/paymentOrder/status").asText()).isEqualTo("CONFIRMADA");
        assertThat(confirmData.at("/paymentOrder/metodoConfirmado").asText()).isEqualTo("TPA");
        assertThat(confirmData.at("/paymentOrder/valorRecebido").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(confirmData.at("/paymentOrder/troco").decimalValue()).isEqualByComparingTo("0.00");

        assertThat(applicationEvents.stream(PaymentConfirmedForFiscalIssueEvent.class)
                .filter(event -> setup.pedidoId.equals(event.pedidoId()))
                .count()).isEqualTo(1L);

        String replayJson = mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/payment-order/confirm")
                        .header("Idempotency-Key", "owner-confirm-payment-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(replayJson).at("/data/statusFinanceiro").asText()).isEqualTo("PAGO");
        assertThat(applicationEvents.stream(PaymentConfirmedForFiscalIssueEvent.class)
                .filter(event -> setup.pedidoId.equals(event.pedidoId()))
                .count()).isEqualTo(1L);

        Pedido pedido = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.EM_ANDAMENTO);
        assertThat(pedido.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.PAGO);

        OrdemPagamento ordem = ordemPagamentoRepository
                .findTopByTenantIdAndPedidoIdOrderByCreatedAtDesc(setup.tenant.getId(), setup.pedidoId)
                .orElseThrow();
        assertThat(ordem.getStatus()).isEqualTo(OrdemPagamentoStatus.CONFIRMADA);
        assertThat(ordem.getConfirmadoPorUser().getId()).isEqualTo(owner.getId());

        var pagamentos = pagamentoGatewayRepository.findByPedidoIdOrderByCreatedAtDesc(setup.pedidoId);
        assertThat(pagamentos).hasSize(1);
        assertThat(pagamentos.getFirst().getStatus()).isEqualTo(StatusPagamentoGateway.CONFIRMADO);
        assertThat(pagamentos.getFirst().getGatewayChargeId()).isNull();
        assertThat(pagamentos.getFirst().getExternalReference()).isNull();

        var events = operationalEventLogRepository.searchByTenantAndFilters(
                setup.tenant.getId(), setup.pedidoId, null, OperationalEventType.ORDEM_PAGAMENTO_CONFIRMADA_MANUAL,
                null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 20)
        );
        assertThat(events.getContent().stream().anyMatch(e -> e.getEntityId().equals(ordem.getId()))).isTrue();
    }

    @Test
    @WithMockUser(username = "cashier-cash-user")
    void tenantPdvCashConfirmationRequiresWebCashSessionAndRecordsChange() throws Exception {
        Setup setup = setupTenantAndPedido("pdv-cash-confirm", "P14");
        User cashier = criarTenantActor(setup.tenant, TenantUserRole.TENANT_CASHIER, "cashier-cash-confirm");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), cashier.getId(),
                Set.of(TenantUserRole.TENANT_CASHIER.name()),
                TenantResolutionSource.JWT, false, false
        ));
        abrirTurno(setup, cashier);

        mockMvc.perform(get("/tenant/pdv/payment-methods")
                        .param("unidadeAtendimentoId", String.valueOf(setup.unidadeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code").value(org.hamcrest.Matchers.hasItems("CASH", "TPA")))
                .andExpect(jsonPath("$.data[*].code").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("APPYPAY"))));

        String created = mockMvc.perform(post("/tenant/pedidos")
                        .header("Idempotency-Key", "pdv-cash-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientRequestId": "pdv-cash-create",
                                  "instituicaoId": %d,
                                  "unidadeAtendimentoId": %d,
                                  "metodoPagamento": "CASH",
                                  "itens": [{"produtoId": %d, "quantidade": 2}]
                                }
                                """.formatted(setup.instituicaoId, setup.unidadeId, setup.produtoId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long pedidoId = objectMapper.readTree(created).at("/data/id").asLong();

        ConfirmarPedidoPaymentOrderRequest confirm = new ConfirmarPedidoPaymentOrderRequest();
        confirm.setClientRequestId("pdv-cash-confirm");
        confirm.setMetodoConfirmado(MetodoPagamentoManual.CASH);
        confirm.setValorRecebido(new BigDecimal("25.00"));

        mockMvc.perform(patch("/tenant/pedidos/" + pedidoId + "/payment-order/confirm")
                        .header("Idempotency-Key", "pdv-cash-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/tenant/caixa-operador/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"instituicaoId": %d, "unidadeAtendimentoId": %d}
                                """.formatted(setup.instituicaoId, setup.unidadeId)))
                .andExpect(status().isOk());

        String confirmed = mockMvc.perform(patch("/tenant/pedidos/" + pedidoId + "/payment-order/confirm")
                        .header("Idempotency-Key", "pdv-cash-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paymentOrder = objectMapper.readTree(confirmed).at("/data/paymentOrder");
        assertThat(paymentOrder.at("/metodoConfirmado").asText()).isEqualTo("CASH");
        assertThat(paymentOrder.at("/valorRecebido").decimalValue()).isEqualByComparingTo("25.00");
        assertThat(paymentOrder.at("/troco").decimalValue()).isEqualByComparingTo("5.00");

        OrdemPagamento ordem = ordemPagamentoRepository
                .findTopByTenantIdAndPedidoIdOrderByCreatedAtDesc(setup.tenant.getId(), pedidoId)
                .orElseThrow();
        assertThat(ordem.getCaixaOperadorSession()).isNotNull();
        assertThat(ordem.getCaixaOperadorSession().getId()).isNotNull();
        assertThat(ordem.getMetodoConfirmado()).isEqualTo(MetodoPagamentoManual.CASH);
        assertThat(ordem.getTroco()).isEqualByComparingTo("5.00");
        var inventoryConsumption = inventoryConsumptionRecordRepository
                .findByTenantIdAndPedidoId(setup.tenant.getId(), pedidoId)
                .orElseThrow();
        assertThat(inventoryConsumption.getStatus()).isEqualTo(InventoryConsumptionStatus.CONSUMED);

        long caixaId = ordem.getCaixaOperadorSession().getId();
        mockMvc.perform(post("/tenant/caixa-operador/" + caixaId + "/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"declaredCashAmount": 20.00, "declaredTpaAmount": 0.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.expectedCashAmount").value(20.00))
                .andExpect(jsonPath("$.data.cashDifferenceAmount").value(0.00));
        mockMvc.perform(get("/tenant/caixa-operador/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @WithMockUser(username = "owner-user")
    void expiredPaymentOrder_blocksConfirmation_andRemovesConfirmPaymentAction() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-pay-exp", "P13");
        User owner = criarTenantActor(setup.tenant, TenantUserRole.TENANT_OWNER, "owner-pay-exp");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), owner.getId(),
                Set.of(TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/aceitar"))
                .andExpect(status().isOk());

        OrdemPagamento ordem = ordemPagamentoRepository
                .findTopByTenantIdAndPedidoIdOrderByCreatedAtDesc(setup.tenant.getId(), setup.pedidoId)
                .orElseThrow();
        ordem.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        ordemPagamentoRepository.saveAndFlush(ordem);

        String tenantDetail = mockMvc.perform(get("/tenant/pedidos/" + setup.pedidoId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode tenantData = objectMapper.readTree(tenantDetail).at("/data");
        assertThat(tenantData.at("/paymentOrder/status").asText()).isEqualTo("EXPIRADA");
        assertThat(allowedActionsContain(tenantData, PedidoAllowedAction.CONFIRM_PAYMENT)).isFalse();
        assertThat(tenantData.at("/actionReasons/CONFIRM_PAYMENT").asText()).isEqualTo("Ordem de pagamento expirada.");

        ConfirmarPedidoPaymentOrderRequest request = new ConfirmarPedidoPaymentOrderRequest();
        request.setClientRequestId("owner-confirm-payment-expired");
        request.setMetodoConfirmado(MetodoPagamentoManual.TPA);
        request.setReferenciaOperador("TPA-EXP");

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/payment-order/confirm")
                        .header("Idempotency-Key", "owner-confirm-payment-expired")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        OrdemPagamento expired = ordemPagamentoRepository
                .findTopByTenantIdAndPedidoIdOrderByCreatedAtDesc(setup.tenant.getId(), setup.pedidoId)
                .orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO);
        assertThat(expired.isExpirada(LocalDateTime.now())).isTrue();

        Pedido pedido = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(pedido.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.NAO_PAGO);
        assertThat(pagamentoGatewayRepository.findByPedidoIdOrderByCreatedAtDesc(setup.pedidoId)).isEmpty();
    }

    @Test
    @WithMockUser(username = "operator-user")
    void operator_canRejectCreatedNaoPagoPedido_andEventLogIsCreated() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-9", "OS9");
        Pedido created = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        created.setStatusFinanceiro(StatusFinanceiroPedido.NAO_PAGO);
        pedidoRepository.saveAndFlush(created);

        User operator = criarTenantActor(setup.tenant, TenantUserRole.TENANT_OPERATOR, "operator-os9");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), operator.getId(),
                Set.of(TenantUserRole.TENANT_OPERATOR.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/rejeitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Fora do horário de produção\"}"))
                .andExpect(status().isOk());

        Pedido pedido = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(pedido.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.NAO_PAGO);

        var sub = subPedidoRepository.findByIdAndTenantId(setup.subPedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(sub.getStatus()).isEqualTo(StatusSubPedido.CANCELADO);

        var events = operationalEventLogRepository.searchByTenantAndFilters(
                setup.tenant.getId(), setup.pedidoId, null, OperationalEventType.PEDIDO_STATUS_CHANGED,
                null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 20)
        );
        assertThat(events.getContent().stream().anyMatch(e -> "CANCELADO".equals(e.getStatusNovo()))).isTrue();
    }

    @Test
    @WithMockUser(username = "operator-user")
    void rejectPedido_isBlockedAfterAccept() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-10", "O10");
        Pedido created = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        created.setStatusFinanceiro(StatusFinanceiroPedido.NAO_PAGO);
        pedidoRepository.saveAndFlush(created);

        User operator = criarTenantActor(setup.tenant, TenantUserRole.TENANT_OPERATOR, "operator-os10");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), operator.getId(),
                Set.of(TenantUserRole.TENANT_OPERATOR.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/aceitar"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/rejeitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Tarde demais\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "operator-user")
    void acceptPedidoFromOtherTenantIsNotFound() throws Exception {
        Setup tenantA = setupTenantAndPedido("op-status-11a", "11A");
        Setup tenantB = setupTenantAndPedido("op-status-11b", "11B");
        User operatorA = criarTenantActor(tenantA.tenant, TenantUserRole.TENANT_OPERATOR, "operator-os11");
        TenantContextHolder.set(new TenantContext(
                tenantA.tenant.getId(), tenantA.tenant.getTenantCode(), operatorA.getId(),
                Set.of(TenantUserRole.TENANT_OPERATOR.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + tenantB.pedidoId + "/aceitar"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "cashier-pdv")
    void tenantPdvCreatesServerPricedOrderWithPaymentOrderAndIdempotentReplay() throws Exception {
        Setup setup = setupTenantAndPedido("pdv-create-1", "PC1");
        User cashier = criarTenantActor(setup.tenant, TenantUserRole.TENANT_CASHIER, "cashier-pdv-create");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), cashier.getId(),
                Set.of(TenantUserRole.TENANT_CASHIER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        String payload = """
                {
                  "clientRequestId": "pdv-create-request-1",
                  "instituicaoId": %d,
                  "unidadeAtendimentoId": %d,
                  "metodoPagamento": "TPA",
                  "itens": [
                    { "produtoId": %d, "quantidade": 2 }
                  ]
                }
                """.formatted(setup.instituicaoId, setup.unidadeId, setup.produtoId);

        mockMvc.perform(post("/tenant/pedidos")
                        .header("Idempotency-Key", "pdv-create-idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());

        abrirTurno(setup, cashier);

        String firstResponse = mockMvc.perform(post("/tenant/pedidos")
                        .header("Idempotency-Key", "pdv-create-idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode firstData = objectMapper.readTree(firstResponse).at("/data");
        long createdPedidoId = firstData.at("/id").asLong();
        assertThat(firstData.at("/pedidoOrigem").asText()).isEqualTo("PDV_INTERNO");
        assertThat(firstData.at("/total").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(firstData.at("/statusFinanceiro").asText()).isEqualTo("NAO_PAGO");
        assertThat(firstData.at("/paymentOrder/status").asText()).isEqualTo("AGUARDANDO_CONFIRMACAO");
        assertThat(firstData.at("/paymentOrder/metodoPagamento").asText()).isEqualTo("TPA");

        String replayResponse = mockMvc.perform(post("/tenant/pedidos")
                        .header("Idempotency-Key", "pdv-create-idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(replayResponse).at("/data/id").asLong()).isEqualTo(createdPedidoId);

        String conflictingPayload = payload.replace("\"quantidade\": 2", "\"quantidade\": 3");
        mockMvc.perform(post("/tenant/pedidos")
                        .header("Idempotency-Key", "pdv-create-idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictingPayload))
                .andExpect(status().isConflict());
    }

    private Setup setupTenantAndPedido(String slug, String tenantCode) throws Exception {
        Tenant tenant = criarTenant("Tenant " + slug, slug, tenantCode);
        Instituicao inst = criarInstituicao(tenant, "Inst " + slug, tenantCode.substring(0, Math.min(3, tenantCode.length())), "NIF-" + tenantCode, "+244900" + Math.abs(slug.hashCode() % 1_000_000));
        UnidadeAtendimento unidade = criarUnidade(inst, "Unidade " + slug, TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(unidade, "Cozinha " + slug, TipoCozinha.CENTRAL);

        CategoriaProduto cat = criarCategoria(tenant, "Geral", "geral");
        Produto prod = criarProduto(tenant, cat, "P1-" + tenantCode, "Produto " + tenantCode, new BigDecimal("10.00"));
        publicarCardapioForTest(tenant.getId());

        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenant.getId(), inst.getId(), unidade.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR " + slug
        );

        String payload = """
                { "itens": [ { "produtoId": %d, "quantidade": 1 } ] }
                """.formatted(prod.getId());

        String resp = mockMvc.perform(post("/public/q/" + qr.getToken() + "/pedidos")
                        .header("Idempotency-Key", "idem-" + slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        Long pedidoId = json.at("/data/pedidoId").asLong();
        var subs = subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId);
        Long subPedidoId = subs.getFirst().getId();

        return new Setup(tenant, pedidoId, subPedidoId, qr.getToken(), inst.getId(), unidade.getId(), prod.getId());
    }

    private boolean allowedActionsContain(JsonNode data, PedidoAllowedAction action) {
        JsonNode actions = data.at("/allowedActions");
        if (!actions.isArray()) {
            return false;
        }
        for (JsonNode item : actions) {
            if (action.name().equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private Tenant criarTenant(String nome, String slug, String tenantCode) {
        Tenant t = new Tenant();
        t.setNome(nome);
        t.setSlug(slug);
        t.setTenantCode(tenantCode);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        t.setTemplateCode("CONSUMA_REST_V1");
        return tenantRepository.saveAndFlush(t);
    }

    private Instituicao criarInstituicao(Tenant tenant, String nome, String sigla, String nif, String telefoneAutorizacao) {
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome(nome);
        i.setSigla(sigla);
        i.setNif(nif);
        i.setTelefoneAutorizacao(telefoneAutorizacao);
        i.setAtiva(true);
        return instituicaoRepository.saveAndFlush(i);
    }

    private UnidadeAtendimento criarUnidade(Instituicao instituicao, String nome, TipoUnidadeAtendimento tipo) {
        UnidadeAtendimento u = new UnidadeAtendimento();
        u.setNome(nome);
        u.setTipo(tipo);
        u.setAtiva(true);
        u.setInstituicao(instituicao);
        return unidadeAtendimentoRepository.saveAndFlush(u);
    }

    private void criarCozinhaVinculada(UnidadeAtendimento unidade, String nome, TipoCozinha tipo) {
        Cozinha c = new Cozinha();
        c.setNome(nome);
        c.setTipo(tipo);
        c.setAtiva(true);
        Cozinha salva = cozinhaRepository.saveAndFlush(c);
        unidade.adicionarCozinha(salva);
        unidadeAtendimentoRepository.saveAndFlush(unidade);
    }

    private CategoriaProduto criarCategoria(Tenant tenant, String nome, String slug) {
        CategoriaProduto c = new CategoriaProduto();
        c.setTenant(tenant);
        c.setNome(nome);
        c.setSlug(slug);
        c.setOrdem(0);
        c.setAtivo(true);
        return categoriaProdutoRepository.saveAndFlush(c);
    }

    private Produto criarProduto(Tenant tenant, CategoriaProduto categoriaProduto, String codigo, String nome, BigDecimal preco) {
        Produto p = new Produto();
        p.setTenant(tenant);
        p.setCodigo(codigo);
        p.setNome(nome);
        p.setPreco(preco);
        p.setAtivo(true);
        p.setCategoriaProduto(categoriaProduto);
        p.setCategoria(com.restaurante.model.enums.CategoriaProdutoLegacy.OUTROS);
        return produtoRepository.saveAndFlush(p);
    }

    private User criarTenantActor(Tenant tenant, TenantUserRole role, String prefix) {
        User user = new User();
        user.setUsername(UniqueTestData.uniqueUsername(prefix));
        user.setPassword("x");
        user.setEmail(UniqueTestData.uniqueEmail(prefix));
        user.setTelefone(UniqueTestData.uniqueTelefone());
        user.setRoles(Set.of(Role.ROLE_GERENTE));
        user.setAtivo(true);
        user = userRepository.saveAndFlush(user);

        TenantUser membership = new TenantUser();
        membership.setTenant(tenant);
        membership.setUser(user);
        membership.setRole(role);
        membership.setEstado(TenantUserEstado.ATIVO);
        tenantUserRepository.saveAndFlush(membership);
        return user;
    }

    private void abrirTurno(Setup setup, User actor) {
        TurnoOperacional turno = new TurnoOperacional();
        turno.setTenant(setup.tenant);
        turno.setInstituicao(instituicaoRepository.findById(setup.instituicaoId).orElseThrow());
        turno.setUnidadeAtendimento(unidadeAtendimentoRepository.findById(setup.unidadeId).orElseThrow());
        turno.setAbertoPor(actor);
        turno.setStatus(TurnoOperacionalStatus.ABERTO);
        turno.setTipo(TurnoOperacionalTipo.BALCAO);
        turno.setNome("Turno PDV test");
        turno.setAbertoEm(LocalDateTime.now());
        turnoOperacionalRepository.saveAndFlush(turno);
    }

    private record Setup(
            Tenant tenant,
            Long pedidoId,
            Long subPedidoId,
            String qrToken,
            Long instituicaoId,
            Long unidadeId,
            Long produtoId
    ) {}
}
