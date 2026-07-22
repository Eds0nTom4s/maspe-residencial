package com.restaurante.businesstemplate.templates;

import com.restaurante.businesstemplate.BusinessTemplate;
import com.restaurante.businesstemplate.BusinessTemplateKey;
import com.restaurante.businesstemplate.dto.BusinessTemplatePreviewResponse;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionRequest;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionResponse;
import com.restaurante.businesstemplate.support.BusinessTemplateProvisioningSupport;
import com.restaurante.exception.ProvisioningException;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantOperacaoPolicy;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.LogisticsMode;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TenantUserAccessOrigin;
import com.restaurante.model.enums.TipoSessao;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ConsumaPontoV1Template implements BusinessTemplate {

    public static final String CODE = "CONSUMA_PONTO";
    public static final int VERSION = 1;

    private final BusinessTemplateProvisioningSupport support;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public int version() {
        return VERSION;
    }

    @Override
    public BusinessTemplatePreviewResponse preview(BusinessTemplateKey key, BusinessTemplateProvisionRequest request) {
        List<BusinessTemplatePreviewResponse.ValidationMessage> bloqueios = new ArrayList<>();
        List<BusinessTemplatePreviewResponse.ValidationMessage> avisos = new ArrayList<>();

        Plano plano = null;
        try {
            plano = support.resolvePlanoOrDefault(request != null ? request.getPlanoCodigo() : null);
        } catch (ProvisioningException ex) {
            bloqueios.add(block(ex.getCode(), ex.getField(), ex.getMessage(), ex.getDetail(), ex.getRecommendedAction()));
        }

        if (request == null || request.getTenant() == null) {
            bloqueios.add(block("TENANT_DADOS_OBRIGATORIOS", "tenant", "Dados do tenant são obrigatórios.", null, "Preencha tenant.*"));
            return basePreview(bloqueios, avisos, plano, 0, 0, 0, 0, 0, false, false);
        }

        if (request.getOwner() == null) {
            bloqueios.add(block("OWNER_DADOS_OBRIGATORIOS", "owner", "Owner é obrigatório.", null, "Preencha owner.*"));
        } else {
            boolean hasPhone = request.getOwner().getTelefone() != null && !request.getOwner().getTelefone().isBlank();
            boolean hasEmail = request.getOwner().getEmail() != null && !request.getOwner().getEmail().isBlank();
            if (!hasPhone && !hasEmail) {
                bloqueios.add(block("OWNER_CONTACTO_OBRIGATORIO", "owner", "Owner deve conter email ou telefone.", null, "Informe owner.email ou owner.telefone."));
            }
        }

        String slugNorm = support.normalizeSlug(request.getTenant().getSlug());
        if (slugNorm == null || slugNorm.isBlank()) {
            bloqueios.add(block("TENANT_SLUG_INVALIDO", "tenant.slug", "Slug inválido.", null, "Informe um slug não vazio."));
        } else if (support.tenantSlugExists(slugNorm)) {
            bloqueios.add(block("TENANT_SLUG_DUPLICADO", "tenant.slug", "Slug já existe: " + slugNorm, null, "Escolha outro slug."));
        }

        String tenantCodeNorm = support.normalizeTenantCode(request.getTenant().getTenantCode());
        if (tenantCodeNorm == null || tenantCodeNorm.isBlank()) {
            String generated = support.gerarTenantCodeUnico(slugNorm);
            avisos.add(warn("TENANT_CODE_SERA_GERADO", "tenant.tenantCode", "tenantCode será gerado automaticamente.", "Sugestão: " + generated, "Revise o tenantCode sugerido."));
            tenantCodeNorm = generated;
        } else if (support.tenantCodeExists(tenantCodeNorm)) {
            bloqueios.add(block("TENANT_CODE_DUPLICADO", "tenant.tenantCode", "tenantCode já existe: " + tenantCodeNorm, null, "Escolha outro tenantCode."));
        }

        boolean entregaManual = request.getPonto() != null && Boolean.TRUE.equals(request.getPonto().getEntregaManual());
        boolean allowPickup = request.getPonto() == null || request.getPonto().getAllowPickup() == null || Boolean.TRUE.equals(request.getPonto().getAllowPickup());

        // Recursos planejados (PONTO é simples)
        int unidades = 1;
        int users = 1;
        int qr = 1;
        int dispositivos = 0;
        int categorias = 5;

        return previewWithLimits(bloqueios, avisos, plano, unidades, users, qr, dispositivos, categorias,
                false, false, entregaManual, allowPickup);
    }

    @Transactional
    @Override
    public BusinessTemplateProvisionResponse provision(BusinessTemplateKey key, BusinessTemplateProvisionRequest request) {
        if (request == null || request.getTenant() == null) {
            throw new ProvisioningException(HttpStatus.BAD_REQUEST, "TENANT_DADOS_OBRIGATORIOS", "tenant",
                    "Dados do tenant são obrigatórios.", null, "Preencha tenant.*", null);
        }
        support.assertOwnerHasContact(request.getOwner());

        Plano plano = support.resolvePlanoOrDefault(request.getPlanoCodigo());

        String slugNorm = support.normalizeSlug(request.getTenant().getSlug());
        support.assertSlugUnique(slugNorm);

        String tenantCodeNorm = support.normalizeTenantCode(request.getTenant().getTenantCode());
        if (tenantCodeNorm == null || tenantCodeNorm.isBlank()) {
            tenantCodeNorm = support.gerarTenantCodeUnico(slugNorm);
        }
        support.assertTenantCodeUnique(tenantCodeNorm);

        boolean entregaManual = request.getPonto() != null && Boolean.TRUE.equals(request.getPonto().getEntregaManual());
        boolean allowPickup = request.getPonto() == null || request.getPonto().getAllowPickup() == null || Boolean.TRUE.equals(request.getPonto().getAllowPickup());

        Tenant tenant = support.criarTenant(
                request.getTenant(),
                slugNorm,
                tenantCodeNorm,
                CODE,
                VERSION,
                "PLATFORM_TEMPLATE_API",
                request.getBusinessAccountId()
        );
        Subscricao sub = support.criarSubscricaoAtiva(tenant, plano);

        // Limites
        int unidadesNovas = 1;
        int usuariosNovos = 1;
        int qrNovos = 1;
        int dispositivosNovos = 0;
        support.assertPlanLimitsOrThrow(tenant.getId(), unidadesNovas, usuariosNovos, qrNovos, dispositivosNovos);

        Instituicao inst = support.criarInstituicaoDefault(tenant, request);
        UnidadeAtendimento ua = support.criarUnidadeAtendimentoPrincipal(inst, "Unidade Principal", TipoUnidadeAtendimento.CAFETERIA);

        List<CategoriaProduto> categorias = support.criarCategorias(tenant,
                List.of("Bebidas", "Lanches", "Produtos rápidos", "Serviços", "Outros"),
                List.of("bebidas", "lanches", "produtos-rapidos", "servicos", "outros"));
        support.criarProdutos(tenant, List.of(
                new BusinessTemplateProvisioningSupport.ProdutoTemplateSeed("bebidas", "PONTO-AGUA-500", "Água", "Água 500ml", "500", CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA, 2),
                new BusinessTemplateProvisioningSupport.ProdutoTemplateSeed("bebidas", "PONTO-REFRI", "Refrigerante", "Refrigerante", "800", CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA, 2),
                new BusinessTemplateProvisioningSupport.ProdutoTemplateSeed("lanches", "PONTO-CACHORRO-QUENTE", "Cachorro quente", "Cachorro quente simples", "1200", CategoriaProdutoLegacy.LANCHE, 8),
                new BusinessTemplateProvisioningSupport.ProdutoTemplateSeed("lanches", "PONTO-SANDES-SIMPLES", "Sandes simples", "Sandes simples", "1000", CategoriaProdutoLegacy.LANCHE, 6),
                new BusinessTemplateProvisioningSupport.ProdutoTemplateSeed("servicos", "PONTO-SERVICO-RAPIDO", "Serviço rápido", "Serviço rápido", "1000", CategoriaProdutoLegacy.OUTROS, 5)
        ));
        support.configurarCardapioInicial(tenant, 5, 20);

        var ownerUser = support.criarOuReusarOwnerUser(request.getOwner(), ua);
        TenantUser ownerLink = support.criarTenantUser(
                tenant,
                ownerUser,
                TenantUserRole.TENANT_OWNER,
                ua,
                TenantUserAccessOrigin.BUSINESS_ACCOUNT_OWNER
        );

        QrCodeOperacional qr = support.criarQrPrincipal(tenant, inst, ua, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR Principal");

        support.bootstrapPaymentAndInventory(tenant);
        support.upsertDeliveryPolicy(tenant, allowPickup, entregaManual, false);

        TenantOperacaoPolicy op = support.upsertOperacaoPolicy(
                tenant,
                false,
                entregaManual ? LogisticsMode.TENANT_MANUAL : LogisticsMode.NONE,
                allowPickup,
                true,
                true,
                "SIMPLE",
                false,
                false,
                false,
                false,
                false,
                false
        );
        support.upsertOperationalModules(tenant, false, true, false, false, true, false);
        support.upsertSessaoConsumoConfig(tenant, false, false, false, TipoSessao.PRE_PAGO,
                true, true, true, false, 12);

        support.logTemplateProvisioned(tenant, CODE, VERSION, plano.getCodigo());

        return BusinessTemplateProvisionResponse.builder()
                .templateCode(CODE)
                .templateVersion(VERSION)
                .provisionedAt(LocalDateTime.now())
                .tenantId(tenant.getId())
                .tenantNome(tenant.getNome())
                .tenantSlug(tenant.getSlug())
                .tenantCode(tenant.getTenantCode())
                .planoCodigo(plano.getCodigo())
                .subscricaoId(sub.getId())
                .instituicaoId(inst.getId())
                .unidadeAtendimentoId(ua.getId())
                .ownerUserId(ownerUser.getId())
                .ownerEmail(ownerUser.getEmail())
                .ownerTelefone(ownerUser.getTelefone())
                .qrPrincipal(BusinessTemplateProvisionResponse.QrProvisionado.builder()
                        .qrCodeId(qr.getId())
                        .qrToken(qr.getToken())
                        .qrUrlPublica(support.buildPublicQrUrl(qr.getToken()))
                        .tipo(qr.getTipo() != null ? qr.getTipo().name() : null)
                        .nome(qr.getNome())
                        .build())
                .categorias(categorias.stream().map(c -> BusinessTemplateProvisionResponse.CategoriaProvisionada.builder()
                        .categoriaId(c.getId())
                        .nome(c.getNome())
                        .slug(c.getSlug())
                        .build()).toList())
                .politicasAplicadas(toPolicies(op))
                .mensagens(List.of("Provisionamento CONSUMA_PONTO_V1 concluído com sucesso"))
                .build();
    }

    private BusinessTemplatePreviewResponse basePreview(
            List<BusinessTemplatePreviewResponse.ValidationMessage> bloqueios,
            List<BusinessTemplatePreviewResponse.ValidationMessage> avisos,
            Plano plano,
            int unidades,
            int users,
            int qr,
            int dispositivos,
            int categorias,
            boolean entregaManual,
            boolean allowPickup
    ) {
        return previewWithLimits(bloqueios, avisos, plano, unidades, users, qr, dispositivos, categorias,
                false, false, entregaManual, allowPickup);
    }

    private BusinessTemplatePreviewResponse previewWithLimits(
            List<BusinessTemplatePreviewResponse.ValidationMessage> bloqueios,
            List<BusinessTemplatePreviewResponse.ValidationMessage> avisos,
            Plano plano,
            int unidades,
            int users,
            int qr,
            int dispositivos,
            int categorias,
            boolean requireOpenTurno,
            boolean allowTableQr,
            boolean entregaManual,
            boolean allowPickup
    ) {
        BusinessTemplatePreviewResponse.PlanResources resources = BusinessTemplatePreviewResponse.PlanResources.builder()
                .tenantsCriados(1)
                .instituicoesCriadas(1)
                .unidadesAtendimentoCriadas(unidades)
                .categoriasCriadas(categorias)
                .usuariosCriados(users)
                .tenantUsersCriados(users)
                .mesasCriadas(0)
                .qrCodesCriados(qr)
                .dispositivosCriados(dispositivos)
                .unidadesProducaoCriadas(0)
                .rotasProducaoCriadas(0)
                .checklistTemplatesCriados(0)
                .checklistItemsCriados(0)
                .build();

        BusinessTemplatePreviewResponse.PlanLimitsPreview limits = TemplatePreviewLimitCalculator.calculate(plano, resources);

        BusinessTemplatePreviewResponse.TemplatePoliciesPreview pol = BusinessTemplatePreviewResponse.TemplatePoliciesPreview.builder()
                .requireOpenTurnoForOrders(requireOpenTurno)
                .logisticsMode(entregaManual ? LogisticsMode.TENANT_MANUAL.name() : LogisticsMode.NONE.name())
                .allowPickup(allowPickup)
                .allowManualPayment(true)
                .allowDigitalPayment(true)
                .stockMode("SIMPLE")
                .productionEnabled(false)
                .posEnabled(false)
                .kdsEnabled(false)
                .allowTableQr(allowTableQr)
                .snapshotFinanceiroEnabled(false)
                .preFechoEnabled(false)
                .build();

        boolean permitido = bloqueios == null || bloqueios.isEmpty();
        return BusinessTemplatePreviewResponse.builder()
                .templateCode(CODE)
                .templateVersion(VERSION)
                .permitido(permitido && (limits == null || limits.getPrecisaOverride() == null || !limits.getPrecisaOverride()))
                .bloqueios(bloqueios)
                .avisos(avisos)
                .recursosPlanejados(resources)
                .limites(limits)
                .politicas(pol)
                .build();
    }

    private BusinessTemplatePreviewResponse.ValidationMessage block(String code, String field, String message, String detail, String action) {
        return BusinessTemplatePreviewResponse.ValidationMessage.builder()
                .code(code)
                .field(field)
                .message(message)
                .detail(detail)
                .recommendedAction(action)
                .build();
    }

    private BusinessTemplatePreviewResponse.ValidationMessage warn(String code, String field, String message, String detail, String action) {
        return BusinessTemplatePreviewResponse.ValidationMessage.builder()
                .code(code)
                .field(field)
                .message(message)
                .detail(detail)
                .recommendedAction(action)
                .build();
    }

    private BusinessTemplatePreviewResponse.TemplatePoliciesPreview toPolicies(TenantOperacaoPolicy op) {
        if (op == null) return null;
        return BusinessTemplatePreviewResponse.TemplatePoliciesPreview.builder()
                .requireOpenTurnoForOrders(op.isRequireOpenTurnoForOrders())
                .logisticsMode(op.getLogisticsMode() != null ? op.getLogisticsMode().name() : null)
                .allowPickup(op.isAllowPickup())
                .allowManualPayment(op.isAllowManualPayment())
                .allowDigitalPayment(op.isAllowDigitalPayment())
                .stockMode(op.getStockMode())
                .productionEnabled(op.isProductionEnabled())
                .posEnabled(op.isPosEnabled())
                .kdsEnabled(op.isKdsEnabled())
                .allowTableQr(op.isAllowTableQr())
                .snapshotFinanceiroEnabled(op.isSnapshotFinanceiroEnabled())
                .preFechoEnabled(op.isPreFechoEnabled())
                .build();
    }
}
