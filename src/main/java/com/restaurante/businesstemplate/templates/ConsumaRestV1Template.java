package com.restaurante.businesstemplate.templates;

import com.restaurante.businesstemplate.BusinessTemplate;
import com.restaurante.businesstemplate.BusinessTemplateKey;
import com.restaurante.businesstemplate.dto.BusinessTemplatePreviewResponse;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionRequest;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionResponse;
import com.restaurante.businesstemplate.support.BusinessTemplateProvisioningSupport;
import com.restaurante.exception.ProvisioningException;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.ChecklistOperacionalTemplate;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.RotaProducaoCategoria;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantOperacaoPolicy;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.UnidadeProducao;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.LogisticsMode;
import com.restaurante.model.enums.OperationalDeviceType;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.service.producao.RotaProducaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ConsumaRestV1Template implements BusinessTemplate {

    public static final String CODE = "CONSUMA_REST";
    public static final int VERSION = 1;

    private final BusinessTemplateProvisioningSupport support;
    private final RotaProducaoService rotaProducaoService;

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
            return basePreview(bloqueios, avisos, plano, 0, 0, 0, 0, 0, false, false, 0);
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

        boolean temMesas = request.getRest() != null && Boolean.TRUE.equals(request.getRest().getTemMesas());
        int quantidadeMesas = 0;
        if (temMesas) {
            Integer q = request.getRest().getQuantidadeMesas();
            if (q == null) {
                bloqueios.add(block("QUANTIDADE_MESAS_OBRIGATORIA", "rest.quantidadeMesas",
                        "quantidadeMesas é obrigatória quando temMesas=true.", null, "Informe rest.quantidadeMesas."));
            } else if (q < 0) {
                bloqueios.add(block("QUANTIDADE_MESAS_INVALIDA", "rest.quantidadeMesas", "quantidadeMesas inválida.", null, "Informe quantidadeMesas >= 0."));
            } else if (q > 200) {
                bloqueios.add(block("QUANTIDADE_MESAS_EXCEDE_TETO", "rest.quantidadeMesas", "quantidadeMesas excede teto técnico (200).", null, "Reduza a quantidade de mesas."));
            } else {
                quantidadeMesas = q;
            }
        }

        boolean gerarQrPorMesa = request.getRest() == null || request.getRest().getGerarQrPorMesa() == null || Boolean.TRUE.equals(request.getRest().getGerarQrPorMesa());
        boolean temBarSeparado = request.getRest() != null && Boolean.TRUE.equals(request.getRest().getTemBarSeparado());
        boolean exigeTurnoAberto = request.getRest() == null || request.getRest().getExigeTurnoAberto() == null || Boolean.TRUE.equals(request.getRest().getExigeTurnoAberto());

        int categorias = 4;
        int unidadesAtendimento = 1;
        int users = 1;
        int dispositivos = 2; // POS + KDS cozinha
        int qr = 1 + (temMesas && gerarQrPorMesa ? quantidadeMesas : 0);
        int mesas = temMesas ? quantidadeMesas : 0;
        int unidadesProducao = temBarSeparado ? 3 : 2; // geral + cozinha (+bar)
        int rotas = 4;
        int checklistTemplates = 2;
        int checklistItems = 6;

        BusinessTemplatePreviewResponse.PlanResources resources = BusinessTemplatePreviewResponse.PlanResources.builder()
                .tenantsCriados(1)
                .instituicoesCriadas(1)
                .unidadesAtendimentoCriadas(unidadesAtendimento)
                .categoriasCriadas(categorias)
                .usuariosCriados(users)
                .tenantUsersCriados(users)
                .mesasCriadas(mesas)
                .qrCodesCriados(qr)
                .dispositivosCriados(dispositivos)
                .unidadesProducaoCriadas(unidadesProducao)
                .rotasProducaoCriadas(rotas)
                .checklistTemplatesCriados(checklistTemplates)
                .checklistItemsCriados(checklistItems)
                .build();

        BusinessTemplatePreviewResponse.PlanLimitsPreview limits = TemplatePreviewLimitCalculator.calculate(plano, resources);

        boolean deliveryManual = request.getRest() != null && request.getRest().getEntrega() == BusinessTemplateProvisionRequest.RestDeliveryOption.MANUAL;
        boolean deliveryNetwork = request.getRest() != null && request.getRest().getEntrega() == BusinessTemplateProvisionRequest.RestDeliveryOption.CONSUMA_NETWORK;
        boolean allowPickup = true;

        BusinessTemplatePreviewResponse.TemplatePoliciesPreview pol = BusinessTemplatePreviewResponse.TemplatePoliciesPreview.builder()
                .requireOpenTurnoForOrders(exigeTurnoAberto)
                .logisticsMode((deliveryNetwork ? LogisticsMode.CONSUMA_NETWORK : (deliveryManual ? LogisticsMode.TENANT_MANUAL : LogisticsMode.NONE)).name())
                .allowPickup(allowPickup)
                .allowManualPayment(true)
                .allowDigitalPayment(true)
                .stockMode("OPTIONAL")
                .productionEnabled(true)
                .posEnabled(true)
                .kdsEnabled(true)
                .allowTableQr(temMesas)
                .snapshotFinanceiroEnabled(true)
                .preFechoEnabled(true)
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

    private BusinessTemplatePreviewResponse basePreview(
            List<BusinessTemplatePreviewResponse.ValidationMessage> bloqueios,
            List<BusinessTemplatePreviewResponse.ValidationMessage> avisos,
            Plano plano,
            int unidades,
            int users,
            int qr,
            int dispositivos,
            int categorias,
            boolean temMesas,
            boolean temBarSeparado,
            int quantidadeMesas
    ) {
        BusinessTemplatePreviewResponse.PlanResources resources = BusinessTemplatePreviewResponse.PlanResources.builder()
                .tenantsCriados(1)
                .instituicoesCriadas(1)
                .unidadesAtendimentoCriadas(unidades)
                .categoriasCriadas(categorias)
                .usuariosCriados(users)
                .tenantUsersCriados(users)
                .mesasCriadas(temMesas ? quantidadeMesas : 0)
                .qrCodesCriados(qr)
                .dispositivosCriados(dispositivos)
                .unidadesProducaoCriadas(temBarSeparado ? 3 : 2)
                .rotasProducaoCriadas(4)
                .checklistTemplatesCriados(2)
                .checklistItemsCriados(6)
                .build();

        BusinessTemplatePreviewResponse.PlanLimitsPreview limits = TemplatePreviewLimitCalculator.calculate(plano, resources);

        return BusinessTemplatePreviewResponse.builder()
                .templateCode(CODE)
                .templateVersion(VERSION)
                .permitido(bloqueios == null || bloqueios.isEmpty())
                .bloqueios(bloqueios)
                .avisos(avisos)
                .recursosPlanejados(resources)
                .limites(limits)
                .build();
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

        boolean temMesas = request.getRest() != null && Boolean.TRUE.equals(request.getRest().getTemMesas());
        int quantidadeMesas = temMesas ? (request.getRest().getQuantidadeMesas() != null ? request.getRest().getQuantidadeMesas() : 0) : 0;
        if (temMesas && quantidadeMesas < 0) {
            throw new ProvisioningException(HttpStatus.BAD_REQUEST, "QUANTIDADE_MESAS_INVALIDA", "rest.quantidadeMesas",
                    "quantidadeMesas inválida.", null, "Informe quantidadeMesas >= 0.", null);
        }
        if (temMesas && quantidadeMesas > 200) {
            throw new ProvisioningException(HttpStatus.BAD_REQUEST, "QUANTIDADE_MESAS_EXCEDE_TETO", "rest.quantidadeMesas",
                    "quantidadeMesas excede teto técnico (200).", null, "Reduza a quantidade de mesas.", null);
        }
        boolean gerarQrPorMesa = request.getRest() == null || request.getRest().getGerarQrPorMesa() == null || Boolean.TRUE.equals(request.getRest().getGerarQrPorMesa());
        boolean temBarSeparado = request.getRest() != null && Boolean.TRUE.equals(request.getRest().getTemBarSeparado());
        boolean exigeTurnoAberto = request.getRest() == null || request.getRest().getExigeTurnoAberto() == null || Boolean.TRUE.equals(request.getRest().getExigeTurnoAberto());

        boolean deliveryManual = request.getRest() != null && request.getRest().getEntrega() == BusinessTemplateProvisionRequest.RestDeliveryOption.MANUAL;
        boolean deliveryNetwork = request.getRest() != null && request.getRest().getEntrega() == BusinessTemplateProvisionRequest.RestDeliveryOption.CONSUMA_NETWORK;

        Tenant tenant = support.criarTenant(request.getTenant(), slugNorm, tenantCodeNorm, CODE, VERSION, "PLATFORM_TEMPLATE_API");
        Subscricao sub = support.criarSubscricaoAtiva(tenant, plano);

        int unidadesNovas = 1;
        int usuariosNovos = 1;
        int dispositivosNovos = 2;
        int qrNovos = 1 + (temMesas && gerarQrPorMesa ? quantidadeMesas : 0);
        support.assertPlanLimitsOrThrow(tenant.getId(), unidadesNovas, usuariosNovos, qrNovos, dispositivosNovos);

        Instituicao inst = support.criarInstituicaoDefault(tenant, request);
        TipoUnidadeAtendimento uaTipo = request.getTenant().getTipo() == com.restaurante.model.enums.TenantTipo.BAR
                ? TipoUnidadeAtendimento.BAR
                : TipoUnidadeAtendimento.RESTAURANTE;
        UnidadeAtendimento ua = support.criarUnidadeAtendimentoPrincipal(inst, "Unidade Principal", uaTipo);

        List<CategoriaProduto> categorias = support.criarCategorias(tenant,
                List.of("Comidas", "Bebidas", "Sobremesas", "Promoções"),
                List.of("comidas", "bebidas", "sobremesas", "promocoes"));

        var ownerUser = support.criarOuReusarOwnerUser(request.getOwner(), ua);
        support.criarTenantUser(tenant, ownerUser, TenantUserRole.TENANT_OWNER, ua);

        QrCodeOperacional qrPrincipal = support.criarQrPrincipal(tenant, inst, ua, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR Principal");

        List<Mesa> mesas = temMesas ? support.criarMesas(tenant, inst, ua, quantidadeMesas, "Mesa") : List.of();
        List<BusinessTemplateProvisionResponse.MesaProvisionada> mesasResp = new ArrayList<>();
        if (temMesas && gerarQrPorMesa) {
            for (Mesa m : mesas) {
                QrCodeOperacional qrm = support.criarQrPorMesa(tenant, inst, ua, m);
                mesasResp.add(BusinessTemplateProvisionResponse.MesaProvisionada.builder()
                        .mesaId(m.getId())
                        .numero(m.getNumero())
                        .referencia(m.getReferencia())
                        .qrCodeId(qrm.getId())
                        .qrToken(qrm.getToken())
                        .qrUrlPublica(support.buildPublicQrUrl(qrm.getToken()))
                        .build());
            }
        } else if (temMesas) {
            for (Mesa m : mesas) {
                mesasResp.add(BusinessTemplateProvisionResponse.MesaProvisionada.builder()
                        .mesaId(m.getId())
                        .numero(m.getNumero())
                        .referencia(m.getReferencia())
                        .build());
            }
        }

        List<UnidadeProducao> unidadesProducao = support.criarUnidadesProducaoRest(tenant, inst, ua, temBarSeparado);

        UnidadeProducao cozinha = unidadesProducao.stream().filter(u -> "COZINHA".equals(u.getCodigo())).findFirst().orElse(unidadesProducao.getFirst());
        UnidadeProducao bar = unidadesProducao.stream().filter(u -> "BAR".equals(u.getCodigo())).findFirst().orElse(null);

        // Rotas padrão: comidas->cozinha, bebidas->bar|cozinha, sobremesas->cozinha, promocoes->geral
        List<BusinessTemplateProvisionResponse.RotaProducaoProvisionada> rotasResp = new ArrayList<>();
        for (CategoriaProduto c : categorias) {
            UnidadeProducao target;
            if ("bebidas".equalsIgnoreCase(c.getSlug())) {
                target = bar != null ? bar : cozinha;
            } else if ("promocoes".equalsIgnoreCase(c.getSlug())) {
                target = unidadesProducao.stream().filter(u -> "GERAL".equals(u.getCodigo())).findFirst().orElse(cozinha);
            } else {
                target = cozinha;
            }
            RotaProducaoCategoria rota = rotaProducaoService.configurarRota(tenant.getId(), c.getId(), target.getId(), 0);
            rotasResp.add(BusinessTemplateProvisionResponse.RotaProducaoProvisionada.builder()
                    .rotaId(rota.getId())
                    .categoriaId(c.getId())
                    .categoriaSlug(c.getSlug())
                    .unidadeProducaoId(target.getId())
                    .unidadeProducaoCodigo(target.getCodigo())
                    .prioridade(rota.getPrioridade())
                    .build());
        }

        // Devices: POS + KDS cozinha
        DispositivoOperacional pos = support.criarDeviceRest(tenant, inst, ua, null,
                "POS-PRINCIPAL", "POS Principal", DispositivoTipo.POS, OperationalDeviceType.POS_CAIXA);
        DispositivoOperacional kds = support.criarDeviceRest(tenant, inst, ua, cozinha,
                "KDS-COZINHA", "KDS Cozinha", DispositivoTipo.KDS, OperationalDeviceType.KDS_COZINHA);

        List<BusinessTemplateProvisionResponse.DispositivoProvisionado> devicesResp = List.of(
                BusinessTemplateProvisionResponse.DispositivoProvisionado.builder()
                        .dispositivoId(pos.getId())
                        .codigo(pos.getCodigo())
                        .nome(pos.getNome())
                        .tipo(pos.getTipo() != null ? pos.getTipo().name() : null)
                        .operationalDeviceType(pos.getOperationalDeviceType() != null ? pos.getOperationalDeviceType().name() : null)
                        .status(pos.getStatus() != null ? pos.getStatus().name() : null)
                        .build(),
                BusinessTemplateProvisionResponse.DispositivoProvisionado.builder()
                        .dispositivoId(kds.getId())
                        .codigo(kds.getCodigo())
                        .nome(kds.getNome())
                        .tipo(kds.getTipo() != null ? kds.getTipo().name() : null)
                        .operationalDeviceType(kds.getOperationalDeviceType() != null ? kds.getOperationalDeviceType().name() : null)
                        .status(kds.getStatus() != null ? kds.getStatus().name() : null)
                        .unidadeProducaoId(cozinha.getId())
                        .build()
        );

        // Checklists padrão tenant-specific
        ChecklistOperacionalTemplate abertura = support.criarChecklistTemplate(
                tenant,
                com.restaurante.model.enums.ChecklistTipo.ABERTURA,
                "Checklist Abertura (REST default)",
                List.of(
                        support.checklistItem("CAIXA_OK", "Caixa conferido e pronto", true),
                        support.checklistItem("TPA_OK", "TPA testado/funcional", true),
                        support.checklistItem("COZINHA_OK", "Cozinha pronta para produção", true)
                )
        );
        ChecklistOperacionalTemplate fecho = support.criarChecklistTemplate(
                tenant,
                com.restaurante.model.enums.ChecklistTipo.FECHO,
                "Checklist Fecho (REST default)",
                List.of(
                        support.checklistItem("CAIXA_FECHADO", "Caixa fechado e conferido", true),
                        support.checklistItem("LIMPEZA_OK", "Limpeza concluída", true),
                        support.checklistItem("SNAPSHOT_OK", "Snapshot financeiro gerado", true)
                )
        );
        List<BusinessTemplateProvisionResponse.ChecklistProvisionado> checklistsResp = List.of(
                BusinessTemplateProvisionResponse.ChecklistProvisionado.builder()
                        .templateId(abertura.getId())
                        .tipo(abertura.getTipo().name())
                        .nome(abertura.getNome())
                        .totalItens(3)
                        .build(),
                BusinessTemplateProvisionResponse.ChecklistProvisionado.builder()
                        .templateId(fecho.getId())
                        .tipo(fecho.getTipo().name())
                        .nome(fecho.getNome())
                        .totalItens(3)
                        .build()
        );

        support.bootstrapPaymentAndInventory(tenant);
        support.upsertDeliveryPolicy(tenant, true, deliveryManual, deliveryNetwork);

        LogisticsMode logisticsMode = deliveryNetwork ? LogisticsMode.CONSUMA_NETWORK : (deliveryManual ? LogisticsMode.TENANT_MANUAL : LogisticsMode.NONE);
        TenantOperacaoPolicy op = support.upsertOperacaoPolicy(
                tenant,
                exigeTurnoAberto,
                logisticsMode,
                true,
                true,
                true,
                "OPTIONAL",
                true,
                true,
                true,
                temMesas,
                true,
                true
        );

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
                        .qrCodeId(qrPrincipal.getId())
                        .qrToken(qrPrincipal.getToken())
                        .qrUrlPublica(support.buildPublicQrUrl(qrPrincipal.getToken()))
                        .tipo(qrPrincipal.getTipo() != null ? qrPrincipal.getTipo().name() : null)
                        .nome(qrPrincipal.getNome())
                        .build())
                .categorias(categorias.stream().map(c -> BusinessTemplateProvisionResponse.CategoriaProvisionada.builder()
                        .categoriaId(c.getId())
                        .nome(c.getNome())
                        .slug(c.getSlug())
                        .build()).toList())
                .mesas(mesasResp)
                .unidadesProducao(unidadesProducao.stream().map(u -> BusinessTemplateProvisionResponse.UnidadeProducaoProvisionada.builder()
                        .unidadeProducaoId(u.getId())
                        .codigo(u.getCodigo())
                        .nome(u.getNome())
                        .tipo(u.getTipo() != null ? u.getTipo().name() : null)
                        .build()).toList())
                .rotasProducao(rotasResp)
                .dispositivos(devicesResp)
                .checklists(checklistsResp)
                .politicasAplicadas(toPolicies(op))
                .mensagens(List.of("Provisionamento CONSUMA_REST_V1 concluído com sucesso"))
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
