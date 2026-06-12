package com.restaurante.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.BusinessAccountMember;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.repository.BusinessAccountMemberRepository;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.web-application-type=servlet",
                "jwt.secret=0123456789ABCDEF0123456789ABCDEF",
                "jwt.expiration=3600000"
        }
)
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("it-postgres")
@org.springframework.transaction.annotation.Transactional
class TenantProvisioningAccessIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired BusinessAccountMemberRepository businessAccountMemberRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void provisionWithAccess_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/platform/tenants/provision-with-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "createBusinessAccount": true,
                                  "businessAccountNome": "Conta sem token",
                                  "tenantNome": "Tenant sem token",
                                  "tenantTipo": "VENDEDOR_RUA",
                                  "tenantTelefone": "+244910000001",
                                  "ownerNome": "Owner sem token",
                                  "ownerTelefone": "+244910000002"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void platformAdmin_canProvisionTenantWithAccess_login_select_andPublicOrderAppearsInTenantPedidos() throws Exception {
        String suffix = String.format("%06d", Math.abs(System.nanoTime() % 1_000_000L));
        String payload = """
                {
                  "createBusinessAccount": true,
                  "businessAccountNome": "Conta Provisionada %s",
                  "businessAccountEmail": "conta-%s@test.com",
                  "businessAccountTelefone": "+244920%s",
                  "maxTenants": 1,
                  "tenantNome": "Ponto Provisionado %s",
                  "tenantSlug": "ponto-provisionado-%s",
                  "tenantTipo": "VENDEDOR_RUA",
                  "tenantNif": "NIF-%s",
                  "tenantEmail": "tenant-%s@test.com",
                  "tenantTelefone": "+244930%s",
                  "ownerNome": "Owner Provisionado %s",
                  "ownerUsername": "owner.prov.%s",
                  "ownerEmail": "owner-%s@test.com",
                  "ownerTelefone": "+244940%s",
                  "gerarSenhaTemporaria": true,
                  "ativarTenant": true,
                  "observacao": "Provisionamento IT"
                }
                """.formatted(
                suffix, suffix, suffix.substring(0, 6),
                suffix, suffix, suffix,
                suffix, suffix.substring(0, 6),
                suffix, suffix, suffix, suffix.substring(0, 6)
        );

        String resp = mockMvc.perform(post("/platform/tenants/provision-with-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/success").asBoolean()).isTrue();
        long tenantId = json.at("/data/tenantId").asLong();
        long ownerUserId = json.at("/data/ownerUserId").asLong();
        long tenantUserId = json.at("/data/tenantUserId").asLong();
        long businessAccountId = json.at("/data/businessAccountId").asLong();
        long businessAccountMemberId = json.at("/data/businessAccountMemberId").asLong();
        String ownerUsername = json.at("/data/ownerUsername").asText();
        String temporaryPassword = json.at("/data/temporaryPassword").asText();
        String qrToken = json.at("/data/qrToken").asText();

        assertThat(tenantId).isPositive();
        assertThat(ownerUserId).isPositive();
        assertThat(tenantUserId).isPositive();
        assertThat(businessAccountId).isPositive();
        assertThat(businessAccountMemberId).isPositive();
        assertThat(ownerUsername).isNotBlank();
        assertThat(temporaryPassword).isNotBlank();
        assertThat(qrToken).startsWith("q_");
        assertThat(json.at("/data/mustChangePassword").asBoolean()).isTrue();

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        assertThat(tenant.getTipo()).isEqualTo(TenantTipo.VENDEDOR_RUA);
        assertThat(tenant.getBusinessAccount()).isNotNull();
        assertThat(tenant.getBusinessAccount().getId()).isEqualTo(businessAccountId);

        User owner = userRepository.findById(ownerUserId).orElseThrow();
        assertThat(owner.getPassword()).isNotEqualTo(temporaryPassword);
        assertThat(passwordEncoder.matches(temporaryPassword, owner.getPassword())).isTrue();
        assertThat(owner.getMustChangePassword()).isTrue();
        assertThat(owner.getTemporaryPasswordExpiresAt()).isNotNull();
        assertThat(owner.getRoles()).extracting(Enum::name).contains("ROLE_GERENTE");

        TenantUser tenantUser = tenantUserRepository.findByTenantIdAndUserId(tenantId, ownerUserId).orElseThrow();
        assertThat(tenantUser.getId()).isEqualTo(tenantUserId);
        assertThat(tenantUser.getRole()).isEqualTo(TenantUserRole.TENANT_OWNER);

        BusinessAccountMember member = businessAccountMemberRepository
                .findByBusinessAccountIdAndUserId(businessAccountId, ownerUserId)
                .orElseThrow();
        assertThat(member.getId()).isEqualTo(businessAccountMemberId);
        assertThat(member.getRole()).isEqualTo(BusinessAccountRole.OWNER);
        assertThat(member.getEstado()).isEqualTo(BusinessAccountMemberEstado.ATIVO);

        String loginResp = mockMvc.perform(post("/auth/jwt/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(ownerUsername, temporaryPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode loginJson = objectMapper.readTree(loginResp);
        String globalToken = loginJson.at("/data/accessToken").asText();
        assertThat(globalToken).isNotBlank();
        assertThat(loginJson.at("/data/mustChangePassword").asBoolean()).isTrue();

        String tenantsResp = mockMvc.perform(get("/auth/tenants")
                        .header("Authorization", "Bearer " + globalToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode tenantsJson = objectMapper.readTree(tenantsResp);
        assertThat(tenantsJson.at("/data/0/tenantId").asLong()).isEqualTo(tenantId);

        String selectResp = mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "tenantId": %d }
                                """.formatted(tenantId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tenantToken = objectMapper.readTree(selectResp).at("/data/accessToken").asText();
        assertThat(tenantToken).isNotBlank();

        mockMvc.perform(get("/tenant/cardapio/status")
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk());

        String accessResp = mockMvc.perform(get("/platform/tenants/{tenantId}/access", tenantId)
                        .with(user("platform-admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode accessJson = objectMapper.readTree(accessResp);
        assertThat(accessJson.at("/data/tenantId").asLong()).isEqualTo(tenantId);
        assertThat(accessJson.at("/data/owner/userId").asLong()).isEqualTo(ownerUserId);
        assertThat(accessJson.at("/data/owner/mustChangePassword").asBoolean()).isTrue();

        String resetResp = mockMvc.perform(post("/platform/tenants/{tenantId}/access/reset-password", tenantId)
                        .with(user("platform-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "motivo": "Reset IT"
                                }
                                """.formatted(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode resetJson = objectMapper.readTree(resetResp);
        String resetPassword = resetJson.at("/data/temporaryPassword").asText();
        assertThat(resetJson.at("/data/userId").asLong()).isEqualTo(ownerUserId);
        assertThat(resetPassword).isNotBlank();
        assertThat(passwordEncoder.matches(resetPassword, userRepository.findById(ownerUserId).orElseThrow().getPassword())).isTrue();

        mockMvc.perform(get("/platform/tenants")
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isForbidden());

        CategoriaProduto categoria = categoriaProdutoRepository.findBySlugAndTenantId("bebida-nao-alcoolica", tenantId)
                .orElseGet(() -> criarCategoriaBebida(tenant));
        criarCozinhaVinculada(tenantId, TipoCozinha.BAR_PREP);
        Produto produto = new Produto();
        produto.setTenant(tenant);
        produto.setCodigo("SKU-" + suffix);
        produto.setNome("Produto Provisionado");
        produto.setPreco(new BigDecimal("50.00"));
        produto.setCategoria(CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA);
        produto.setCategoriaProduto(categoria);
        produto.setDisponivel(true);
        produto.setAtivo(true);
        produtoRepository.saveAndFlush(produto);
        publicarCardapioForTest(tenantId);

        String orderResp = mockMvc.perform(post("/public/q/{token}/pedidos", qrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "prov-access-" + suffix)
                        .content("""
                                {
                                  "clienteNome": "Cliente Provisionado",
                                  "clienteTelefone": "+244950%s",
                                  "metodoPagamento": "%s",
                                  "itens": [
                                    { "produtoId": %d, "quantidade": 1 }
                                  ]
                                }
                                """.formatted(suffix.substring(0, 6), PaymentMethodCode.CASH.name(), produto.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long pedidoId = objectMapper.readTree(orderResp).at("/data/pedidoId").asLong();
        Pedido pedido = pedidoRepository.findByIdAndTenantIdComSessaoConsumo(pedidoId, tenantId).orElseThrow();
        assertThat(pedido.getSessaoConsumo()).isNull();

        String pedidosResp = mockMvc.perform(get("/tenant/pedidos?page=0&size=20")
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode pedidosJson = objectMapper.readTree(pedidosResp).at("/data/content");
        assertThat(pedidosJson.isArray()).isTrue();
        boolean foundPedido = false;
        for (JsonNode pedidoNode : pedidosJson) {
            if (pedidoNode.path("id").asLong() == pedidoId) {
                foundPedido = true;
                break;
            }
        }
        assertThat(foundPedido).isTrue();
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void provisionWithAccess_respectsBusinessAccountMaxTenants() throws Exception {
        String suffix = String.format("%06d", Math.abs(System.nanoTime() % 1_000_000L));
        String firstPayload = """
                {
                  "createBusinessAccount": true,
                  "businessAccountNome": "Conta Limite %s",
                  "maxTenants": 1,
                  "tenantNome": "Tenant A %s",
                  "tenantSlug": "tenant-a-%s",
                  "tenantTipo": "VENDEDOR_RUA",
                  "tenantTelefone": "+244960%s",
                  "ownerNome": "Owner A",
                  "ownerTelefone": "+244970%s"
                }
                """.formatted(suffix, suffix, suffix, suffix.substring(0, 6), suffix.substring(0, 6));

        String firstResp = mockMvc.perform(post("/platform/tenants/provision-with-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long businessAccountId = objectMapper.readTree(firstResp).at("/data/businessAccountId").asLong();

        String secondPayload = """
                {
                  "businessAccountId": %d,
                  "tenantNome": "Tenant B %s",
                  "tenantSlug": "tenant-b-%s",
                  "tenantTipo": "VENDEDOR_RUA",
                  "tenantTelefone": "+244980%s",
                  "ownerNome": "Owner B",
                  "ownerTelefone": "+244990%s"
                }
                """.formatted(businessAccountId, suffix, suffix, suffix.substring(0, 6), suffix.substring(0, 6));

        String secondResp = mockMvc.perform(post("/platform/tenants/provision-with-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondPayload))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(secondResp).contains("limite maximo de tenants");
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

    private CategoriaProduto criarCategoriaBebida(Tenant tenant) {
        CategoriaProduto categoria = new CategoriaProduto();
        categoria.setTenant(tenant);
        categoria.setNome("Bebida Nao Alcoolica");
        categoria.setSlug("bebida-nao-alcoolica");
        categoria.setOrdem(10);
        categoria.setAtivo(true);
        return categoriaProdutoRepository.saveAndFlush(categoria);
    }

    private void criarCozinhaVinculada(long tenantId, TipoCozinha tipo) {
        Long unidadeAtendimentoId = jdbcTemplate.queryForObject("""
                select ua.id
                from unidades_atendimento ua
                join instituicoes i on i.id = ua.instituicao_id
                where i.tenant_id = ?
                order by ua.id
                limit 1
                """, Long.class, tenantId);

        Cozinha cozinha = new Cozinha();
        cozinha.setNome("Bar Provisionado IT");
        cozinha.setTipo(tipo);
        cozinha.setAtiva(true);
        Cozinha salva = cozinhaRepository.saveAndFlush(cozinha);

        jdbcTemplate.update(
                "insert into unidade_cozinha (unidade_id, cozinha_id) values (?, ?)",
                unidadeAtendimentoId,
                salva.getId()
        );
    }
}
