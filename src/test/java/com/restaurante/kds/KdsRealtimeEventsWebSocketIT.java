package com.restaurante.kds;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.dto.response.kds.KdsRealtimeEventResponse;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.KdsRealtimeEventType;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.JwtChannelInterceptor;
import com.restaurante.security.TenantStompSession;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class KdsRealtimeEventsWebSocketIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired SubPedidoRepository subPedidoRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JwtChannelInterceptor jwtChannelInterceptor;

    @MockBean SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void setUpCozinha() {
        if (cozinhaRepository.findByAtivaAndTipo(true, TipoCozinha.CENTRAL).isEmpty()) {
            Cozinha cozinha = Cozinha.builder()
                    .nome("Cozinha Central KDS Realtime")
                    .tipo(TipoCozinha.CENTRAL)
                    .ativa(true)
                    .build();
            cozinhaRepository.saveAndFlush(cozinha);
        }
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void tenantKds_realtimeEventsArePublishedAfterCommitAndIsolatedByTenant() throws Exception {
        ProvisionarTenantResponse tenantA = provisionTenant("KRTA");
        ProvisionarTenantResponse tenantB = provisionTenant("KRTB");
        Produto produto = criarProduto(tenantA.getTenantId(), "Produto KDS Realtime");
        publicarCardapioForTest(tenantA.getTenantId());

        long pedidoId = criarPedidoPublicoPonto(tenantA.getQrToken(), produto.getId());
        long subPedidoId = subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId).getFirst().getId();

        verify(messagingTemplate, after(500).times(0))
                .convertAndSend(eq("/topic/tenant/%d/kds".formatted(tenantA.getTenantId())), any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/tenant/%d/kds".formatted(tenantB.getTenantId())), any(Object.class));

        TenantContextHolder.set(new TenantContext(
                tenantA.getTenantId(), tenantA.getTenantCode(), tenantA.getOwnerUserId(),
                Set.of("TENANT_OWNER"), TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(post("/tenant/pedidos/{id}/aceitar", pedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacao\":\"Liberar produção realtime\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusOperacional").value("EM_ANDAMENTO"));

        KdsRealtimeEventResponse created = captureTenantEvent(tenantA.getTenantId(), KdsRealtimeEventType.SUBPEDIDO_CREATED);
        assertThat(created.tenantId()).isEqualTo(tenantA.getTenantId());
        assertThat(created.subPedidoId()).isEqualTo(subPedidoId);
        assertThat(created.statusAtual().name()).isEqualTo("PENDENTE");
        assertThat(created.version()).isNotNull();
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/tenant/%d/kds".formatted(tenantB.getTenantId())), any(Object.class));

        reset(messagingTemplate);

        String prepResp = mockMvc.perform(patch("/tenant/kds/subpedidos/{id}/iniciar-preparo", subPedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":%d}".formatted(created.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EM_PREPARACAO"))
                .andReturn().getResponse().getContentAsString();
        long prepVersion = objectMapper.readTree(prepResp).at("/data/version").asLong();

        KdsRealtimeEventResponse started = captureTenantEvent(tenantA.getTenantId(), KdsRealtimeEventType.SUBPEDIDO_STARTED);
        assertThat(started.subPedidoId()).isEqualTo(subPedidoId);
        assertThat(started.statusAtual().name()).isEqualTo("EM_PREPARACAO");
        assertThat(started.version()).isEqualTo(prepVersion);
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/tenant/%d/kds".formatted(tenantB.getTenantId())), any(Object.class));

        reset(messagingTemplate);
        mockMvc.perform(patch("/tenant/kds/subpedidos/{id}/pronto", subPedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":%d}".formatted(created.version())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KDS_SUBPEDIDO_CONFLICT"));
        verifyNoInteractions(messagingTemplate);

        String readyResp = mockMvc.perform(patch("/tenant/kds/subpedidos/{id}/pronto", subPedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":%d}".formatted(prepVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PRONTO"))
                .andReturn().getResponse().getContentAsString();
        long readyVersion = objectMapper.readTree(readyResp).at("/data/version").asLong();

        KdsRealtimeEventResponse ready = captureTenantEvent(tenantA.getTenantId(), KdsRealtimeEventType.SUBPEDIDO_READY);
        assertThat(ready.statusAtual().name()).isEqualTo("PRONTO");
        assertThat(ready.version()).isEqualTo(readyVersion);

        reset(messagingTemplate);
        String deliveredResp = mockMvc.perform(patch("/tenant/kds/subpedidos/{id}/entregar", subPedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":%d}".formatted(readyVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENTREGUE"))
                .andReturn().getResponse().getContentAsString();
        long deliveredVersion = objectMapper.readTree(deliveredResp).at("/data/version").asLong();

        KdsRealtimeEventResponse delivered = captureTenantEvent(tenantA.getTenantId(), KdsRealtimeEventType.SUBPEDIDO_DELIVERED);
        assertThat(delivered.statusAtual().name()).isEqualTo("ENTREGUE");
        assertThat(delivered.version()).isEqualTo(deliveredVersion);
    }

    @Test
    void stompTenantSubscriptions_requireTokenAndBlockGlobalOrCrossTenantAccess() {
        assertThatThrownBy(() -> jwtChannelInterceptor.preSend(connectWithoutToken(), new ExecutorSubscribableChannel()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token JWT");

        assertThatThrownBy(() -> jwtChannelInterceptor.preSend(
                        subscribeAs(new TenantStompSession(1L, null, null, "GLOBAL", true, List.of()),
                                "/topic/tenant/10/kds"),
                        new ExecutorSubscribableChannel()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant-scoped");

        assertThatThrownBy(() -> jwtChannelInterceptor.preSend(
                        subscribeAs(new TenantStompSession(1L, 11L, "T11", "TENANT", false, List.of("TENANT_ADMIN")),
                                "/topic/tenant/10/kds"),
                        new ExecutorSubscribableChannel()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cross-tenant");

        Message<byte[]> allowed = subscribeAs(
                new TenantStompSession(1L, 10L, "T10", "TENANT", false, List.of("TENANT_ADMIN")),
                "/topic/tenant/10/kds/unidade/2");
        assertThat(jwtChannelInterceptor.preSend(allowed, new ExecutorSubscribableChannel())).isSameAs(allowed);
    }

    private KdsRealtimeEventResponse captureTenantEvent(long tenantId, KdsRealtimeEventType eventType) {
        ArgumentCaptor<KdsRealtimeEventResponse> captor = ArgumentCaptor.forClass(KdsRealtimeEventResponse.class);
        verify(messagingTemplate, after(500).atLeastOnce())
                .convertAndSend(eq("/topic/tenant/%d/kds".formatted(tenantId)), captor.capture());
        return findEvent(captor.getAllValues(), eventType);
    }

    private KdsRealtimeEventResponse findEvent(List<KdsRealtimeEventResponse> events, KdsRealtimeEventType eventType) {
        return events.stream()
                .filter(event -> event.eventType() == eventType)
                .findFirst()
                .orElseThrow();
    }

    private Message<byte[]> connectWithoutToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeAs(TenantStompSession session, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "tenant-user",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        auth.setDetails(session);
        accessor.setUser(auth);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private ProvisionarTenantResponse provisionTenant(String prefix) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String suffix = prefix.toLowerCase() + "-" + Math.abs(System.nanoTime() % 1_000_000L);
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + suffix)
                                .slug("tenant-" + suffix)
                                .tenantCode(prefix + Math.abs(System.nanoTime() % 1000))
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + suffix)
                                .sigla(uniqueSigla(prefix))
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email("owner-" + suffix + "@a.com")
                                .telefone("+244900" + Math.abs(System.nanoTime() % 1_000_000L))
                                .criarUsuario(true)
                                .build())
                        .build()
        );
    }

    private Produto criarProduto(long tenantId, String nome) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        CategoriaProduto categoria = new CategoriaProduto();
        categoria.setTenant(tenant);
        categoria.setNome("Geral KDS Realtime");
        categoria.setSlug("geral-kds-rt-" + Math.abs(System.nanoTime() % 100_000L));
        categoria.setAtivo(true);
        categoria = categoriaProdutoRepository.saveAndFlush(categoria);

        Produto produto = Produto.builder()
                .codigo("KDSRT-" + Math.abs(System.nanoTime() % 1_000_000L))
                .nome(nome)
                .preco(new BigDecimal("25.00"))
                .categoria(CategoriaProdutoLegacy.PRATO_PRINCIPAL)
                .ativo(true)
                .build();
        produto.setTenant(tenant);
        produto.setCategoriaProduto(categoria);
        produto.setDisponivel(true);
        return produtoRepository.saveAndFlush(produto);
    }

    private long criarPedidoPublicoPonto(String token, Long produtoId) throws Exception {
        String resp = mockMvc.perform(post("/public/q/{token}/pedidos", token)
                        .header("Idempotency-Key", "kds-realtime-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteNome": "Cliente KDS Realtime",
                                  "clienteTelefone": "+244950000001",
                                  "metodoPagamento": "%s",
                                  "itens": [
                                    { "produtoId": %d, "quantidade": 1, "observacao": "Realtime" }
                                  ]
                                }
                                """.formatted(PaymentMethodCode.CASH.name(), produtoId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(resp);
        return root.at("/data/pedidoId").asLong();
    }

    private void publicarCardapioForTest(long tenantId) {
        jdbcTemplate.update("""
                insert into tenant_cardapio_configs
                    (tenant_id, cardapio_publicado, cardapio_publicado_em, cardapio_publicado_por_user_id,
                     cardapio_atualizado_em, created_at, updated_at, version)
                values (?, true, now(), null, now(), now(), now(), 0)
                on conflict (tenant_id)
                do update set cardapio_publicado = true,
                              cardapio_publicado_em = now(),
                              cardapio_despublicado_em = null,
                              cardapio_motivo_despublicacao = null,
                              cardapio_atualizado_em = now(),
                              updated_at = now()
                """, tenantId);
    }

    private static String uniqueSigla(String prefix) {
        String normalizedPrefix = prefix == null ? "I" : prefix.replaceAll("[^A-Z0-9]", "");
        if (normalizedPrefix.isBlank()) normalizedPrefix = "I";
        if (normalizedPrefix.length() > 3) normalizedPrefix = normalizedPrefix.substring(0, 3);
        return normalizedPrefix + String.format("%07d", Math.abs(System.nanoTime() % 10_000_000L));
    }
}
