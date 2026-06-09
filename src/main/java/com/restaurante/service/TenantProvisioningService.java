package com.restaurante.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ProvisioningException;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.ProvisioningTemplate;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantLimiteOverride;
import com.restaurante.model.entity.TenantOperacaoPolicy;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.ComportamentoPedidoNaoPago;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TipoSessao;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.model.enums.TipoUnidadeConsumo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.PlanoRepository;
import com.restaurante.repository.ProvisioningTemplateRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantLimiteOverrideRepository;
import com.restaurante.repository.TenantOperacaoPolicyRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.provisioning.ProvisioningPlan;
import com.restaurante.service.provisioning.ProvisioningPlanCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final PlanoRepository planoRepository;
    private final SubscricaoRepository subscricaoRepository;
    private final ProvisioningTemplateRepository templateRepository;
    private final TenantLimitService tenantLimitService;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final MesaRepository mesaRepository;
    private final CategoriaProdutoRepository categoriaProdutoRepository;
    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantLimiteOverrideRepository tenantLimiteOverrideRepository;
    private final TenantOperacaoPolicyRepository tenantOperacaoPolicyRepository;
    private final QrCodeOperacionalService qrCodeOperacionalService;
    private final com.restaurante.service.producao.UnidadeProducaoService unidadeProducaoService;
    private final com.restaurante.service.producao.RotaProducaoService rotaProducaoService;
    private final TenantOperationalModulesService tenantOperationalModulesService;
    private final TenantSessaoConsumoConfigService tenantSessaoConsumoConfigService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final ProvisioningPlanCalculator planCalculator;

    @Value("${consuma.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Transactional
    public ProvisionarTenantResponse provisionar(ProvisionarTenantRequest request) {
        tenantGuard.assertPlatformAdmin();

        if (request.getTenant() == null) {
            throw new ProvisioningException(HttpStatus.BAD_REQUEST, "TENANT_DADOS_OBRIGATORIOS", "tenant",
                    "Dados do tenant são obrigatórios.");
        }

        Plano plano = planoRepository.findByCodigo(request.getPlanoCodigo())
                .filter(Plano::getAtivo)
                .orElseThrow(() -> new ProvisioningException(HttpStatus.BAD_REQUEST, "PLANO_INVALIDO", "planoCodigo",
                        "Plano inválido/inativo: " + request.getPlanoCodigo()));

        ProvisioningTemplate template = templateRepository.findByCodigoAndAtivoTrue(request.getTemplateCodigo())
                .orElseThrow(() -> new ProvisioningException(HttpStatus.BAD_REQUEST, "TEMPLATE_INEXISTENTE_OU_INATIVO", "templateCodigo",
                        "Template inválido/inativo: " + request.getTemplateCodigo()));

        ProvisioningPlan plan = planCalculator.calculate(
                request,
                template,
                tenantRepository::existsBySlug,
                tenantRepository::existsByTenantCode
        );

        String slug = plan.slugNormalized();
        if (slug == null || slug.isBlank()) {
            throw new ProvisioningException(HttpStatus.BAD_REQUEST, "TENANT_SLUG_INVALIDO", "tenant.slug", "Slug inválido.");
        }
        if (plan.slugAlreadyExists()) {
            throw new ProvisioningException(HttpStatus.CONFLICT, "TENANT_SLUG_DUPLICADO", "tenant.slug",
                    "Slug já existe: " + slug);
        }

        String tenantCode = plan.tenantCodeNormalized();
        if (tenantCode == null || tenantCode.isBlank()) {
            tenantCode = plan.tenantCodeGenerated();
        } else if (plan.tenantCodeAlreadyExists()) {
            throw new ProvisioningException(HttpStatus.CONFLICT, "TENANT_CODE_DUPLICADO", "tenant.tenantCode",
                    "tenantCode já existe: " + tenantCode);
        }

        boolean ativarTenant = request.getOpcoes() == null || request.getOpcoes().getAtivarTenant() == null || request.getOpcoes().getAtivarTenant();

        // Nota: TenantLimitService bloqueia criação se Tenant != ATIVO.
        // Para provisionamento interno, criamos como ATIVO e, se necessário, rebaixamos para RASCUNHO no final.
        Tenant tenant = new Tenant();
        tenant.setNome(request.getTenant().getNome());
        tenant.setSlug(slug);
        tenant.setTenantCode(tenantCode);
        tenant.setNif(request.getTenant().getNif());
        tenant.setTelefone(request.getTenant().getTelefone());
        tenant.setEmail(request.getTenant().getEmail());
        tenant.setTipo(request.getTenant().getTipo());
        tenant.setEstado(TenantEstado.ATIVO);
        tenant.setTemplateCode(template.getCodigo());
        tenant.setTemplateVersion(template.getVersion() != null ? template.getVersion().intValue() : null);
        tenant.setProvisionedAt(java.time.LocalDateTime.now());
        tenant.setProvisioningSource("LEGACY_PROVISIONING");
        tenant = tenantRepository.saveAndFlush(tenant);

        Subscricao subscricao = new Subscricao();
        subscricao.setTenant(tenant);
        subscricao.setPlano(plano);
        subscricao.setEstado(SubscricaoEstado.ATIVA);
        subscricao.setInicioEm(LocalDate.now());
        if ("PILOTO".equalsIgnoreCase(plano.getCodigo())) {
            subscricao.setFimEm(LocalDate.now().plusDays(90));
        }
        subscricao.setRenovacaoAutomatica(false);
        subscricao = subscricaoRepository.saveAndFlush(subscricao);

        // Override de limites (opcional) para pilotos (ex.: mais QR codes)
        if (request.getLimitesOverride() != null) {
            TenantLimiteOverride ov = new TenantLimiteOverride();
            ov.setTenant(tenant);
            ov.setAtivo(true);
            ov.setMaxInstituicoes(request.getLimitesOverride().getMaxInstituicoes());
            ov.setMaxUnidadesAtendimento(request.getLimitesOverride().getMaxUnidadesAtendimento());
            ov.setMaxUsuarios(request.getLimitesOverride().getMaxUsuarios());
            ov.setMaxQrCodes(request.getLimitesOverride().getMaxQrCodes());
            ov.setMaxDispositivos(request.getLimitesOverride().getMaxDispositivos());
            ov.setMotivo(request.getLimitesOverride().getMotivo());
            ov.setConfiguradoPor(request.getLimitesOverride().getConfiguradoPor());
            ov.setConfiguradoEm(java.time.LocalDateTime.now());
            tenantLimiteOverrideRepository.saveAndFlush(ov);
        }

        // Resolve config final (template + overrides do request) via planCalculator
        boolean criarUnidade = plan.criarUnidadeAtendimentoDefault();
        boolean criarQrPrincipal = plan.criarQrPrincipal();
        boolean criarCategoriaDefault = plan.criarCategoriaDefault();
        boolean criarMesas = plan.criarMesas();
        int quantidadeMesas = plan.quantidadeMesas();
        boolean criarQrPorMesa = plan.criarQrPorMesa();
        String prefixoMesa = plan.prefixoMesa();

        provisionarModulosOperacionaisDefault(tenant, criarMesas, criarQrPorMesa);

        if (quantidadeMesas < 0) {
            throw new ProvisioningException(HttpStatus.BAD_REQUEST, "QUANTIDADE_MESAS_INVALIDA", "opcoes.quantidadeMesas",
                    "quantidadeMesas inválida.");
        }
        if (quantidadeMesas > 200) {
            throw new ProvisioningException(HttpStatus.BAD_REQUEST, "QUANTIDADE_MESAS_EXCEDE_TETO", "opcoes.quantidadeMesas",
                    "quantidadeMesas excede teto técnico (200).");
        }
        if (criarQrPorMesa && !criarMesas) {
            throw new ProvisioningException(HttpStatus.BAD_REQUEST, "CRIAR_QR_POR_MESA_SEM_MESAS", "opcoes.criarQrPorMesa",
                    "criarQrPorMesa exige criarMesas=true.");
        }

        int unidadesNovas = plan.unidadesNovas();
        int usuariosNovos = plan.usuariosNovos();
        int qrNovos = plan.qrNovos();

        // Aplica limites: valida antes de criar recursos operacionais.
        try {
            tenantLimitService.assertCanCreateInstituicao(tenant.getId());
        } catch (BusinessException ex) {
            throw new ProvisioningException(HttpStatus.CONFLICT, "MAX_INSTITUICOES_EXCEDIDO", "limites.maxInstituicoes",
                    "Limite de instituições excedido para o tenant.");
        }
        try {
            tenantLimitService.assertCanCreateUnidadeAtendimento(tenant.getId(), unidadesNovas);
        } catch (BusinessException ex) {
            throw new ProvisioningException(HttpStatus.CONFLICT, "MAX_UNIDADES_EXCEDIDO", "limites.maxUnidadesAtendimento",
                    "Limite de unidades de atendimento excedido para o tenant.");
        }
        try {
            tenantLimitService.assertCanCreateUser(tenant.getId(), usuariosNovos);
        } catch (BusinessException ex) {
            throw new ProvisioningException(HttpStatus.CONFLICT, "MAX_USUARIOS_EXCEDIDO", "limites.maxUsuarios",
                    "Limite de usuários excedido para o tenant.");
        }
        try {
            tenantLimitService.assertCanCreateQrCode(tenant.getId(), qrNovos);
        } catch (BusinessException ex) {
            throw new ProvisioningException(HttpStatus.CONFLICT, "MAX_QR_CODES_EXCEDIDO", "limites.maxQrCodes",
                    "Limite de QR Codes excedido para o tenant.");
        }

        Instituicao instituicao = criarInstituicao(tenant, request);
        UnidadeAtendimento unidade = null;
        if (criarUnidade) {
            unidade = criarUnidadeAtendimentoDefault(instituicao, template);
        }

        ArrayList<String> categoriasCriadas = new ArrayList<>();
        CategoriaProduto categoriaDefault = null;
        if (criarCategoriaDefault) {
            categoriaDefault = criarCategoriaDefault(tenant, template);
            categoriasCriadas.add(categoriaDefault.getSlug());
        }

        User owner = null;
        TenantUser tenantOwner = null;
        if (usuariosNovos > 0) {
            if (request.getResponsavel() == null) {
                throw new ProvisioningException(HttpStatus.BAD_REQUEST, "OWNER_DADOS_OBRIGATORIOS", "responsavel",
                        "responsavel é obrigatório quando criarUsuario=true.");
            }
            owner = criarOuReusarOwnerUser(request, unidade);
            tenantOwner = criarTenantOwner(tenant, owner, unidade);
        }

        QrCodeOperacional qr = null;
        if (criarQrPrincipal) {
            QrCodeOperacionalTipo qrTipo = resolveQrTipoFromTemplate(template);
            String qrNome = resolveQrNomeFromTemplate(template);
            qr = qrCodeOperacionalService.criarQr(
                    tenant.getId(),
                    instituicao.getId(),
                    unidade != null ? unidade.getId() : null,
                    null,
                    qrTipo,
                    qrNome
            );
        }

        // Unidade de produção default + rota default para categoria "geral" (quando criada)
        // Mantém tenants prontos para operação de produção sem configuração manual inicial.
        var unidadeProducaoDefault = unidadeProducaoService.criarDefaultGeral(
                tenant.getId(),
                instituicao.getId(),
                unidade != null ? unidade.getId() : null,
                "Produção Geral",
                com.restaurante.model.enums.UnidadeProducaoTipo.OUTRO
        );
        if (categoriaDefault != null) {
            rotaProducaoService.configurarRota(tenant.getId(), categoriaDefault.getId(), unidadeProducaoDefault.getId(), 0);
        }

        ArrayList<ProvisionarTenantResponse.MesaProvisionadaResponse> mesasResp = new ArrayList<>();
        if (criarMesas) {
            if (unidade == null) {
                throw new ProvisioningException(HttpStatus.BAD_REQUEST, "MESA_EXIGE_UNIDADE", "opcoes.criarUnidadeAtendimentoDefault",
                        "Template exige mesas, mas unidade de atendimento não foi criada.");
            }
            for (int i = 1; i <= quantidadeMesas; i++) {
                Mesa m = new Mesa();
                m.setNumero(i);
                m.setReferencia(prefixoMesa + " " + i);
                m.setQrCode(null); // QR legado não usado
                m.setCapacidade(null);
                m.setAtiva(true);
                m.setTipo(TipoUnidadeConsumo.MESA_FISICA);
                m.setUnidadeAtendimento(unidade);
                m.setTenant(tenant);
                m.setInstituicao(instituicao);
                m = mesaRepository.saveAndFlush(m);

                ProvisionarTenantResponse.MesaProvisionadaResponse.MesaProvisionadaResponseBuilder mb =
                        ProvisionarTenantResponse.MesaProvisionadaResponse.builder()
                                .mesaId(m.getId())
                                .numero(m.getNumero())
                                .referencia(m.getReferencia());

                if (criarQrPorMesa) {
                    QrCodeOperacional qrm = qrCodeOperacionalService.criarQr(
                            tenant.getId(),
                            instituicao.getId(),
                            unidade.getId(),
                            m.getId(),
                            QrCodeOperacionalTipo.MESA,
                            "QR " + m.getReferencia()
                    );
                    mb.qrCodeId(qrm.getId());
                    mb.qrToken(qrm.getToken());
                    mb.qrUrlPublica(buildPublicQrUrl(qrm.getToken()));
                }
                mesasResp.add(mb.build());
            }
        }

        if (!ativarTenant) {
            tenant.setEstado(TenantEstado.RASCUNHO);
            tenant = tenantRepository.saveAndFlush(tenant);
        }

        ProvisionarTenantResponse.ProvisionarTenantResponseBuilder resp = ProvisionarTenantResponse.builder()
                .tenantId(tenant.getId())
                .tenantNome(tenant.getNome())
                .tenantSlug(tenant.getSlug())
                .tenantCode(tenant.getTenantCode())
                .tenantEstado(tenant.getEstado())
                .planoCodigo(plano.getCodigo())
                .subscricaoId(subscricao.getId())
                .instituicaoId(instituicao.getId())
                .instituicaoNome(instituicao.getNome())
                .categoriasCriadas(categoriasCriadas)
                .mensagens(java.util.List.of("Provisionamento concluído com sucesso"));

        if (unidade != null) {
            resp.unidadeAtendimentoId(unidade.getId());
            resp.unidadeAtendimentoNome(unidade.getNome());
        }
        if (owner != null) {
            resp.ownerUserId(owner.getId());
            resp.ownerEmail(owner.getEmail());
        }
        if (qr != null) {
            resp.qrCodeId(qr.getId());
            resp.qrToken(qr.getToken());
            resp.qrUrlPublica(buildPublicQrUrl(qr.getToken()));
        }
        resp.totalMesasCriadas(mesasResp.isEmpty() ? 0 : mesasResp.size());
        resp.totalQrCodesCriados(qrNovos);
        resp.mesas(mesasResp);
        return resp.build();
    }

    private void provisionarModulosOperacionaisDefault(Tenant tenant, boolean criarMesas, boolean criarQrPorMesa) {
        boolean ponto = tenant.getTipo() == com.restaurante.model.enums.TenantTipo.VENDEDOR_RUA
                || tenant.getTipo() == com.restaurante.model.enums.TenantTipo.LOJA
                || "CONSUMA_PONTO".equalsIgnoreCase(tenant.getTemplateCode())
                || "VENDEDOR_RUA".equalsIgnoreCase(tenant.getTemplateCode());

        if (ponto) {
            tenantOperationalModulesService.upsertForTemplate(tenant, false, true, false, false, true, false);
            tenantSessaoConsumoConfigService.upsertForTemplate(
                    tenant,
                    false,
                    false,
                    false,
                    TipoSessao.PRE_PAGO,
                    true,
                    true,
                    true,
                    false,
                    12
            );
            upsertOperacaoPolicyDefault(tenant, false, false, true);
            return;
        }

        tenantOperationalModulesService.upsertForTemplate(tenant, true, true, criarMesas, criarMesas && criarQrPorMesa, true, true);
        tenantSessaoConsumoConfigService.upsertForTemplate(
                tenant,
                true,
                true,
                true,
                TipoSessao.POS_PAGO,
                false,
                true,
                true,
                criarMesas,
                12
        );
        upsertOperacaoPolicyDefault(tenant, true, criarMesas && criarQrPorMesa, false);
    }

    private void upsertOperacaoPolicyDefault(Tenant tenant,
                                             boolean restaurante,
                                             boolean allowTableQr,
                                             boolean pagamentoObrigatorio) {
        TenantOperacaoPolicy policy = tenantOperacaoPolicyRepository.findByTenantId(tenant.getId()).orElseGet(() -> {
            TenantOperacaoPolicy created = new TenantOperacaoPolicy();
            created.setTenant(tenant);
            return created;
        });
        policy.setProductionEnabled(restaurante);
        policy.setPosEnabled(true);
        policy.setKdsEnabled(restaurante);
        policy.setAllowTableQr(allowTableQr);
        policy.setSnapshotFinanceiroEnabled(true);
        policy.setPreFechoEnabled(true);
        policy.setAllowManualPayment(true);
        policy.setAllowDigitalPayment(true);
        policy.setAllowPickup(true);
        policy.setPagamentoObrigatorioAntesDoPedido(pagamentoObrigatorio);
        policy.setPermitirPedidoSemPagamento(!pagamentoObrigatorio);
        policy.setPermitirPosPago(!pagamentoObrigatorio);
        policy.setPermitirCash(true);
        policy.setPermitirPagamentoNaEntrega(!pagamentoObrigatorio);
        policy.setTempoExpiracaoPedidoPendentePagamentoMinutos(15);
        policy.setComportamentoPedidoNaoPago(ComportamentoPedidoNaoPago.CRIAR_PENDENTE);
        tenantOperacaoPolicyRepository.saveAndFlush(policy);
    }

    private Instituicao criarInstituicao(Tenant tenant, ProvisionarTenantRequest request) {
        String sigla = request.getInstituicao().getSigla();
        if (sigla == null || sigla.isBlank()) {
            sigla = tenant.getTenantCode();
        }
        sigla = sigla.trim().toUpperCase(Locale.ROOT);

        String nif = request.getInstituicao().getNif();
        if (nif == null || nif.isBlank()) {
            nif = tenant.getNif();
        }
        if (nif == null || nif.isBlank()) {
            nif = "NIF-" + tenant.getTenantCode();
        }

        String telefoneAut = request.getInstituicao().getTelefone();
        if (telefoneAut == null || telefoneAut.isBlank()) {
            telefoneAut = request.getTenant().getTelefone();
        }
        if (telefoneAut == null || telefoneAut.isBlank()) {
            telefoneAut = request.getResponsavel() != null ? request.getResponsavel().getTelefone() : null;
        }
        if (telefoneAut == null || telefoneAut.isBlank()) {
            telefoneAut = "+244000000000";
        }

        Instituicao instituicao = new Instituicao();
        instituicao.setTenant(tenant);
        instituicao.setNome(request.getInstituicao().getNome());
        instituicao.setSigla(sigla);
        instituicao.setNif(nif);
        instituicao.setTelefoneAutorizacao(telefoneAut);
        instituicao.setAtiva(true);
        return instituicaoRepository.saveAndFlush(instituicao);
    }

    private UnidadeAtendimento criarUnidadeAtendimentoDefault(Instituicao instituicao, ProvisioningTemplate template) {
        TemplateConfig cfg = parseTemplateConfig(template.getConfiguracaoJson());
        String nome = cfg != null && cfg.unidadeAtendimentoDefault != null && cfg.unidadeAtendimentoDefault.nome != null
                ? cfg.unidadeAtendimentoDefault.nome
                : "Unidade Principal";
        TipoUnidadeAtendimento tipo = cfg != null && cfg.unidadeAtendimentoDefault != null && cfg.unidadeAtendimentoDefault.tipo != null
                ? cfg.unidadeAtendimentoDefault.tipo
                : TipoUnidadeAtendimento.RESTAURANTE;

        UnidadeAtendimento u = new UnidadeAtendimento();
        u.setNome(nome);
        u.setTipo(tipo);
        u.setAtiva(true);
        u.setInstituicao(instituicao);
        return unidadeAtendimentoRepository.saveAndFlush(u);
    }

    private CategoriaProduto criarCategoriaDefault(Tenant tenant, ProvisioningTemplate template) {
        TemplateConfig cfg = parseTemplateConfig(template.getConfiguracaoJson());
        String nome = cfg != null && cfg.categoriaDefault != null && cfg.categoriaDefault.nome != null
                ? cfg.categoriaDefault.nome
                : "Geral";
        String slug = cfg != null && cfg.categoriaDefault != null && cfg.categoriaDefault.slug != null
                ? cfg.categoriaDefault.slug
                : "geral";
        slug = normalizeSlug(slug);
        if (categoriaProdutoRepository.existsBySlugAndTenantId(slug, tenant.getId())) {
            // já existe: devolve a existente
            return categoriaProdutoRepository.findBySlugAndTenantId(slug, tenant.getId()).orElseThrow();
        }
        CategoriaProduto c = new CategoriaProduto();
        c.setTenant(tenant);
        c.setNome(nome);
        c.setSlug(slug);
        c.setOrdem(0);
        c.setAtivo(true);
        return categoriaProdutoRepository.saveAndFlush(c);
    }

    private User criarOuReusarOwnerUser(ProvisionarTenantRequest request, UnidadeAtendimento unidadeDefault) {
        ProvisionarTenantRequest.ResponsavelInfo resp = request.getResponsavel();
        if (resp.getEmail() != null && !resp.getEmail().isBlank()) {
            User existing = userRepository.findByEmail(resp.getEmail()).orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        if (resp.getTelefone() != null && !resp.getTelefone().isBlank()) {
            User existing = userRepository.findByTelefone(resp.getTelefone()).orElse(null);
            if (existing != null) {
                return existing;
            }
        }

        String username = gerarUsername(resp.getEmail(), resp.getTelefone());
        String rawPassword = resp.getSenhaTemporaria();
        if (rawPassword == null || rawPassword.isBlank()) {
            rawPassword = gerarSenhaTemporaria();
        }

        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setEmail(resp.getEmail());
        u.setNomeCompleto(resp.getNome());
        u.setTelefone(resp.getTelefone() != null ? resp.getTelefone() : "+244000000000");
        u.setUnidadeAtendimento(unidadeDefault);
        u.setRoles(Set.of(Role.ROLE_GERENTE));
        u.setAtivo(true);
        return userRepository.saveAndFlush(u);
    }

    private TenantUser criarTenantOwner(Tenant tenant, User user, UnidadeAtendimento unidadeDefault) {
        TenantUser tu = new TenantUser();
        tu.setTenant(tenant);
        tu.setUser(user);
        tu.setRole(TenantUserRole.TENANT_OWNER);
        tu.setEstado(TenantUserEstado.ATIVO);
        tu.setUnidadeAtendimentoDefault(unidadeDefault);
        return tenantUserRepository.saveAndFlush(tu);
    }

    private String buildPublicQrUrl(String token) {
        String base = publicBaseUrl != null ? publicBaseUrl.trim() : "http://localhost:8080";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/q/" + token;
    }

    private static String normalizeSlug(String slug) {
        if (slug == null) return null;
        String s = slug.trim().toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9\\-]+", "-");
        s = s.replaceAll("-{2,}", "-");
        s = s.replaceAll("^-|-$", "");
        return s;
    }

    private static String normalizeTenantCode(String tenantCode) {
        if (tenantCode == null) return null;
        String s = tenantCode.trim().toUpperCase(Locale.ROOT);
        s = s.replaceAll("[^A-Z0-9]", "");
        if (s.length() > 20) s = s.substring(0, 20);
        return s;
    }

    private String gerarTenantCodeUnico(String slug) {
        String base = slug == null ? "TNT" : slug.replaceAll("[^a-z0-9]", "");
        base = base.toUpperCase(Locale.ROOT);
        if (base.length() < 3) base = "TNT";
        if (base.length() > 5) base = base.substring(0, 5);

        String candidate = base;
        int attempt = 0;
        while (tenantRepository.existsByTenantCode(candidate)) {
            attempt++;
            candidate = base + randomCode(3);
            if (candidate.length() > 20) candidate = candidate.substring(0, 20);
            if (attempt > 20) {
                candidate = "T" + randomCode(6);
                if (!tenantRepository.existsByTenantCode(candidate)) break;
            }
        }
        return candidate;
    }

    private static String randomCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CODE_ALPHABET[SECURE_RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private static String gerarUsername(String email, String telefone) {
        if (email != null && !email.isBlank()) {
            return email.toLowerCase(Locale.ROOT);
        }
        if (telefone != null && !telefone.isBlank()) {
            return "u" + telefone.replaceAll("[^0-9]", "");
        }
        return "user-" + randomCode(6);
    }

    private static String gerarSenhaTemporaria() {
        // simples: não logar, não retornar por API nesta fase
        return "Tmp@" + randomCode(8);
    }

    private QrCodeOperacionalTipo resolveQrTipoFromTemplate(ProvisioningTemplate template) {
        TemplateConfig cfg = parseTemplateConfig(template.getConfiguracaoJson());
        if (cfg != null && cfg.qrPrincipal != null && cfg.qrPrincipal.tipo != null) {
            return cfg.qrPrincipal.tipo;
        }
        return QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO;
    }

    private String resolveQrNomeFromTemplate(ProvisioningTemplate template) {
        TemplateConfig cfg = parseTemplateConfig(template.getConfiguracaoJson());
        if (cfg != null && cfg.qrPrincipal != null && cfg.qrPrincipal.nome != null) {
            return cfg.qrPrincipal.nome;
        }
        return "QR Principal";
    }

    private TemplateConfig parseTemplateConfig(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, TemplateConfig.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static class TemplateConfig {
        public UnidadeAtendimentoDefault unidadeAtendimentoDefault;
        public CategoriaDefault categoriaDefault;
        public QrPrincipal qrPrincipal;
        public Boolean criarMesas;
        public Integer quantidadeMesas;
        public Boolean criarQrPorMesa;
        public String prefixoMesa;
    }

    public static class UnidadeAtendimentoDefault {
        public String nome;
        public TipoUnidadeAtendimento tipo;
    }

    public static class CategoriaDefault {
        public String nome;
        public String slug;
    }

    public static class QrPrincipal {
        public String nome;
        public QrCodeOperacionalTipo tipo;
    }
}
