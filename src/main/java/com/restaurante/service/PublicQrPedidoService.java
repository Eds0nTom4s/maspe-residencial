package com.restaurante.service;

import com.restaurante.consumo.identificacao.service.ClienteConsumoService;
import com.restaurante.consumo.identificacao.service.TelefoneNormalizerService;
import com.restaurante.dto.request.PublicQrPedidoItemRequest;
import com.restaurante.dto.request.PublicQrPedidoRequest;
import com.restaurante.dto.request.PublicQrPagamentoRequest;
import com.restaurante.dto.response.PublicQrPedidoItemResponse;
import com.restaurante.dto.response.PublicQrPedidoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.paymentmethod.service.PaymentMethodPolicyResolutionService;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.PublicQrOrderRequest;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantOperationalModulesConfig;
import com.restaurante.model.entity.TenantSessaoConsumoConfig;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.config.OperacaoProperties;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.ComportamentoPedidoNaoPago;
import com.restaurante.model.enums.PaymentDestination;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TipoPagamentoPedido;
import com.restaurante.model.enums.TipoSessao;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.service.kds.KdsRealtimeEventPublisher;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PublicQrPedidoService {

    private final QrCodeOperacionalService qrCodeOperacionalService;
    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final SubPedidoService subPedidoService;
    private final SessaoConsumoService sessaoConsumoService;
    private final PedidoNumberService pedidoNumberService;
    private final PublicQrOrderIdempotencyService idempotencyService;
    private final com.restaurante.service.producao.RotaProducaoService rotaProducaoService;
    private final com.restaurante.service.producao.UnidadeProducaoService unidadeProducaoService;
    private final TurnoOperacionalRepository turnoOperacionalRepository;
    private final OperacaoProperties operacaoProperties;
    private final OperationalEventLogService operationalEventLogService;
    private final PaymentMethodPolicyResolutionService paymentMethodPolicyResolutionService;
    private final TenantCardapioConfigService tenantCardapioConfigService;
    private final TenantPagamentoPolicyService tenantPagamentoPolicyService;
    private final TenantOperationalModulesService tenantOperationalModulesService;
    private final TenantSessaoConsumoConfigService tenantSessaoConsumoConfigService;
    private final ClienteConsumoService clienteConsumoService;
    private final TelefoneNormalizerService telefoneNormalizerService;
    private final com.restaurante.financeiro.service.OrdemPagamentoService ordemPagamentoService;
    private final PublicQrPagamentoService publicQrPagamentoService;
    private final EventLogService eventLogService;
    private final PedidoWorkflowMetadataService pedidoWorkflowMetadataService;
    private final KdsRealtimeEventPublisher kdsRealtimeEventPublisher;

    @Transactional
    public PublicQrPedidoResponse criarPedidoPublicoPorQrToken(String token, String idempotencyKeyHeader, PublicQrPedidoRequest request) {
        QrCodeOperacional qr = qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(token);

        Tenant tenant = qr.getTenant();
        Instituicao instituicao = qr.getInstituicao();
        UnidadeAtendimento unidadeAtendimento = unidadeAtendimentoEfetiva(qr);
        Mesa mesa = qr.getMesa();

        if (!tenantCardapioConfigService.isPublicado(tenant.getId())) {
            throw new BusinessException("Cardápio indisponível no momento. Tente novamente mais tarde.");
        }

        String idemKey = idempotencyService.requireKey(idempotencyKeyHeader, request.getIdempotencyKey());
        String requestHash = idempotencyService.computeRequestHash(request);

        PublicQrOrderIdempotencyService.StartResult start = idempotencyService.startOrGet(tenant, qr, idemKey, requestHash);
        if (start.isCompleted()) {
            return mapPedidoToResponse(start.completedPedido());
        }

        List<Produto> produtos = validarECarregarProdutosDoTenant(tenant.getId(), request.getItens());
        TenantOperationalModulesConfig modules = tenantOperationalModulesService.obterParaTenant(tenant.getId());
        boolean fluxoPonto = isFluxoPonto(tenant);
        SelecaoMetodoPagamento selecaoPagamento = resolverSelecaoMetodoPagamento(request, fluxoPonto);
        ClientePedidoIdentificado cliente = resolverClienteIdentificado(tenant, request, fluxoPonto);
        if (qr.getTipo() == QrCodeOperacionalTipo.MESA && !modules.isQrMesaEnabled()) {
            throw new BusinessException("QR por mesa está indisponível para este negócio.");
        }
        var pagamentoPolicy = tenantPagamentoPolicyService.obterParaTenant(tenant.getId());
        TenantSessaoConsumoConfig sessaoConfig = tenantSessaoConsumoConfigService.obterParaTenant(tenant.getId());
        DecisaoPedidoPublico decisao = decidirFluxoPedidoPublico(qr, modules, sessaoConfig, pagamentoPolicy, mesa);
        if (decisao.deveAguardarPagamento()
                && pagamentoPolicy.getComportamentoPedidoNaoPago() == ComportamentoPedidoNaoPago.BLOQUEAR
                && selecaoPagamento == null) {
            throw new BusinessException("Pagamento obrigatório antes do pedido. Inicie o pagamento para continuar.");
        }

        SessaoConsumo sessao = decisao.usarSessao()
                ? resolverOuCriarSessaoMinima(qr, instituicao, unidadeAtendimento, mesa, decisao.tipoSessao())
                : null;

        PublicQrOrderRequest idemReq = start.request();
        try {
            TurnoOperacional turnoAberto = turnoOperacionalRepository
                    .findOpenByTenantAndInstituicaoAndUnidade(tenant.getId(), instituicao.getId(), unidadeAtendimento.getId())
                    .orElse(null);
            if (turnoAberto == null && operacaoProperties.isRequireOpenTurnoForOrders()) {
                throw new ConflictException("Operação não está aberta para esta unidade (turno não aberto).");
            }

            Pedido pedido = new Pedido();
            pedido.setTenant(tenant);
            pedido.setNumero(pedidoNumberService.gerarNumeroPedido(tenant.getId()));
            pedido.setSessaoConsumo(sessao);
            pedido.setClienteConsumo(cliente != null ? cliente.clienteConsumo() : null);
            pedido.setTurnoOperacional(turnoAberto);
            pedido.setStatus(StatusPedido.CRIADO);
            pedido.setStatusFinanceiro(decisao.statusFinanceiro());
            pedido.setTipoPagamento(decisao.tipoPagamento());
            pedido.setObservacoes(request.getObservacao());

            Map<Cozinha, List<PublicQrPedidoItemRequest>> requestsPorCozinha = agruparItensPorCozinha(unidadeAtendimento.getId(), request.getItens(), produtos);

            pedido = pedidoRepository.save(pedido);

            if (turnoAberto == null) {
                operationalEventLogService.logPedidoSemTurnoAberto(
                        pedido,
                        OperationalOrigem.QR_PUBLICO,
                        "Pedido criado sem turno aberto",
                        Map.of("unidadeAtendimentoId", unidadeAtendimento.getId(), "instituicaoId", instituicao.getId()),
                        null,
                        null
                );
            }

            List<PublicQrPedidoItemResponse> itensResponse = new ArrayList<>();
            int contadorSubPedido = 1;

            for (Map.Entry<Cozinha, List<PublicQrPedidoItemRequest>> entry : requestsPorCozinha.entrySet()) {
                Cozinha cozinha = entry.getKey();
                List<PublicQrPedidoItemRequest> itensReq = entry.getValue();

                SubPedido subPedido = SubPedido.builder()
                        .numero(pedido.getNumero() + "-" + contadorSubPedido)
                        .pedido(pedido)
                        .cozinha(cozinha)
                        .unidadeAtendimento(unidadeAtendimento)
                        .status(StatusSubPedido.CRIADO)
                        .build();
                subPedido.setTenant(tenant);
                pedido.getSubPedidos().add(subPedido);

                // Roteamento de produção:
                // - resolve unidade por categoria para cada item
                // - se houver mais de uma unidade no mesmo SubPedido, usa fallback "GERAL" da instituição
                com.restaurante.model.entity.UnidadeProducao unidadeProducao = null;
                for (PublicQrPedidoItemRequest itemReq : itensReq) {
                    Produto prod = produtos.stream()
                            .filter(p -> p.getId().equals(itemReq.getProdutoId()))
                            .findFirst()
                            .orElseThrow(() -> new BusinessException("Produto inválido ou indisponível."));
                    if (prod.getCategoriaProduto() == null) {
                        throw new BusinessException("Produto inválido ou indisponível.");
                    }
                    var resolved = rotaProducaoService.resolverUnidadeProducaoParaCategoria(
                            tenant.getId(), instituicao.getId(), prod.getCategoriaProduto().getId()
                    );
                    if (unidadeProducao == null) {
                        unidadeProducao = resolved;
                    } else if (!unidadeProducao.getId().equals(resolved.getId())) {
                        unidadeProducao = unidadeProducaoService.obterDefaultParaInstituicao(tenant.getId(), instituicao.getId());
                        break;
                    }
                }
                subPedido.setUnidadeProducao(unidadeProducao);
                contadorSubPedido++;

                for (PublicQrPedidoItemRequest itemReq : itensReq) {
                    Produto produto = produtos.stream()
                            .filter(p -> p.getId().equals(itemReq.getProdutoId()))
                            .findFirst()
                            .orElseThrow(() -> new BusinessException("Produto inválido ou indisponível."));

                    ItemPedido item = ItemPedido.builder()
                            .pedido(pedido)
                            .subPedido(subPedido)
                            .produto(produto)
                            .quantidade(itemReq.getQuantidade())
                            .precoUnitario(produto.getPreco())
                            .observacoes(itemReq.getObservacao())
                            .build();
                    item.setTenant(tenant);
                    item.calcularSubtotal();

                    pedido.adicionarItem(item);
                    subPedido.adicionarItem(item);

                    itensResponse.add(PublicQrPedidoItemResponse.builder()
                            .produtoId(produto.getId())
                            .nome(produto.getNome())
                            .quantidade(itemReq.getQuantidade())
                            .precoUnitario(produto.getPreco())
                            .subtotal(item.getSubtotal())
                            .build());
                }

                subPedido.calcularTotal();
            }

            pedido.calcularTotal();

            if (selecaoPagamento != null) {
                aplicarSelecaoPagamentoAoPedido(pedido, selecaoPagamento);
            }

            pedidoRepository.save(pedido);
            pedido.getSubPedidos().forEach(kdsRealtimeEventPublisher::publishCreatedAfterCommit);

            if (sessao != null) {
                sessaoConsumoService.registrarAtividade(sessao, "Pedido público por QR criado: " + pedido.getNumero());
            }
            eventLogService.registrarEventoPedido(
                    pedido,
                    null,
                    pedido.getStatus(),
                    "PUBLIC_QR",
                    "Pedido criado via QR público"
            );

            if (selecaoPagamento != null) {
                executarFluxoPagamentoSelecionado(token, idemKey, request, pedido, tenant, instituicao, unidadeAtendimento, mesa, turnoAberto, selecaoPagamento);
            }

            idempotencyService.markCompleted(idemReq, pedido);
            return mapPedidoToResponse(pedido);
        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {
            idempotencyService.markFailed(idemReq);
            throw e;
        }
    }

    private UnidadeAtendimento unidadeAtendimentoEfetiva(QrCodeOperacional qr) {
        if (qr.getUnidadeAtendimento() != null) return qr.getUnidadeAtendimento();
        if (qr.getMesa() != null && qr.getMesa().getUnidadeAtendimento() != null) return qr.getMesa().getUnidadeAtendimento();
        throw new BusinessException("QR não possui unidade de atendimento configurada.");
    }

    private List<Produto> validarECarregarProdutosDoTenant(Long tenantId, List<PublicQrPedidoItemRequest> itens) {
        List<Produto> produtos = new ArrayList<>();
        for (PublicQrPedidoItemRequest item : itens) {
            Produto produto = produtoRepository.findByIdAndTenantId(item.getProdutoId(), tenantId)
                    .orElseThrow(() -> new BusinessException("Produto inválido ou indisponível."));
            if (!Boolean.TRUE.equals(produto.getAtivo()) || !Boolean.TRUE.equals(produto.getDisponivel())) {
                throw new BusinessException("Produto inválido ou indisponível.");
            }
            if (produto.getCategoriaProduto() == null
                    || produto.getCategoriaProduto().getTenant() == null
                    || !tenantId.equals(produto.getCategoriaProduto().getTenant().getId())
                    || !Boolean.TRUE.equals(produto.getCategoriaProduto().getAtivo())) {
                throw new BusinessException("Produto inválido ou indisponível.");
            }
            produtos.add(produto);
        }
        return produtos;
    }

    private boolean isFluxoPonto(Tenant tenant) {
        if (tenant == null) {
            return false;
        }
        String templateCode = tenant.getTemplateCode();
        if (templateCode != null && !templateCode.isBlank()) {
            return "CONSUMA_PONTO".equalsIgnoreCase(templateCode)
                    || "VENDEDOR_RUA".equalsIgnoreCase(templateCode)
                    || "LOJA".equalsIgnoreCase(templateCode);
        }
        return tenant.getTipo() == TenantTipo.VENDEDOR_RUA || tenant.getTipo() == TenantTipo.LOJA;
    }

    private SelecaoMetodoPagamento resolverSelecaoMetodoPagamento(PublicQrPedidoRequest request, boolean fluxoPonto) {
        if (request == null) {
            return null;
        }

        if (fluxoPonto && request.getMetodoPagamento() == null) {
            throw new BusinessException("Método de pagamento é obrigatório.");
        }

        if (request.getMetodoPagamento() == null) {
            return null;
        }

        if (request.getMetodoPagamento() == PaymentMethodCode.APPYPAY) {
            return new SelecaoMetodoPagamento(
                    request.getMetodoPagamento(),
                    request.getMetodoPagamentoDigital() != null ? request.getMetodoPagamentoDigital() : com.restaurante.financeiro.enums.MetodoPagamentoAppyPay.GPO
            );
        }

        if (request.getMetodoPagamentoDigital() != null) {
            throw new BusinessException("Método digital informado para pagamento manual.");
        }

        return new SelecaoMetodoPagamento(request.getMetodoPagamento(), null);
    }

    private ClientePedidoIdentificado resolverClienteIdentificado(Tenant tenant, PublicQrPedidoRequest request, boolean fluxoPonto) {
        boolean nomeObrigatorio = fluxoPonto;
        boolean telefoneObrigatorio = fluxoPonto;

        String nome = request != null ? request.getClienteNome() : null;
        String telefone = request != null ? request.getClienteTelefone() : null;

        if (nomeObrigatorio && (nome == null || nome.isBlank())) {
            throw new BusinessException("Nome do cliente é obrigatório");
        }
        if (telefoneObrigatorio && (telefone == null || telefone.isBlank())) {
            throw new BusinessException("Telefone do cliente é obrigatório");
        }
        if ((telefone == null || telefone.isBlank()) && (nome == null || nome.isBlank())) {
            return null;
        }
        if (telefone == null || telefone.isBlank()) {
            if (!fluxoPonto) {
                return null;
            }
            throw new BusinessException("Telefone do cliente é obrigatório");
        }

        String normalizado;
        try {
            normalizado = telefoneNormalizerService.normalizeOrThrow(telefone);
        } catch (BusinessException ex) {
            if ("PHONE_INVALID".equals(ex.getMessage())) {
                throw new BusinessException("Telefone do cliente é inválido");
            }
            throw ex;
        }

        var result = clienteConsumoService.getOrCreateByPhone(tenant, telefone, normalizado, nome);
        return new ClientePedidoIdentificado(result.cliente(), normalizado);
    }

    private void aplicarSelecaoPagamentoAoPedido(Pedido pedido,
                                                 SelecaoMetodoPagamento selecaoPagamento) {
        if (selecaoPagamento == null) {
            return;
        }

        if (selecaoPagamento.isManual()) {
            pedido.setTipoPagamento(TipoPagamentoPedido.POS_PAGO);
            pedido.setStatusFinanceiro(StatusFinanceiroPedido.NAO_PAGO);
            return;
        }

        pedido.setTipoPagamento(TipoPagamentoPedido.PRE_PAGO);
        pedido.setStatusFinanceiro(StatusFinanceiroPedido.PENDENTE_PAGAMENTO);
    }

    private void executarFluxoPagamentoSelecionado(String token,
                                                   String idemKey,
                                                   PublicQrPedidoRequest request,
                                                   Pedido pedido,
                                                   Tenant tenant,
                                                   Instituicao instituicao,
                                                   UnidadeAtendimento unidadeAtendimento,
                                                   Mesa mesa,
                                                   TurnoOperacional turnoAberto,
                                                   SelecaoMetodoPagamento selecaoPagamento) {
        if (selecaoPagamento == null) {
            return;
        }

        paymentMethodPolicyResolutionService.validateForQr(
                tenant.getId(),
                unidadeAtendimento != null ? unidadeAtendimento.getId() : null,
                selecaoPagamento.code(),
                PaymentDestination.PEDIDO,
                pedido.getTotal()
        );

        if (selecaoPagamento.isManual()) {
            ordemPagamentoService.criarOrdemPagamentoPedido(
                    tenant,
                    instituicao,
                    unidadeAtendimento,
                    mesa,
                    turnoAberto,
                    pedido,
                    selecaoPagamento.code() == PaymentMethodCode.CASH
                            ? com.restaurante.model.enums.MetodoPagamentoManual.CASH
                            : com.restaurante.model.enums.MetodoPagamentoManual.TPA,
                    OperationalOrigem.QR_PUBLICO,
                    null,
                    null
            );
            return;
        }

        publicQrPagamentoService.iniciarPagamentoPedidoPorQr(
                token,
                pedido.getId(),
                idemKey + "-payment",
                PublicQrPagamentoRequest.builder()
                        .idempotencyKey(idemKey + "-payment")
                        .metodoPagamento(selecaoPagamento.digitalMethod())
                        .telefone(request.getClienteTelefone())
                        .build()
        );
    }

    private boolean deveAguardarPagamentoAntesDaOperacao(com.restaurante.model.entity.TenantOperacaoPolicy policy) {
        if (policy == null) {
            return true;
        }
        if (!policy.isPagamentoObrigatorioAntesDoPedido()) {
            return false;
        }
        if (policy.isPermitirPedidoSemPagamento() || policy.isPermitirPosPago() || policy.isPermitirPagamentoNaEntrega()) {
            return false;
        }
        return true;
    }

    private DecisaoPedidoPublico decidirFluxoPedidoPublico(
            QrCodeOperacional qr,
            TenantOperationalModulesConfig modules,
            TenantSessaoConsumoConfig sessaoConfig,
            com.restaurante.model.entity.TenantOperacaoPolicy policy,
            Mesa mesa
    ) {
        boolean deveAguardarPagamento = deveAguardarPagamentoAntesDaOperacao(policy);
        boolean usarSessao = modules.isSessaoConsumoEnabled();

        if (!usarSessao) {
            tenantOperationalModulesService.assertPedidoDiretoEnabled(qr.getTenant().getId());
            return new DecisaoPedidoPublico(
                    false,
                    null,
                    deveAguardarPagamento ? StatusFinanceiroPedido.PENDENTE_PAGAMENTO : StatusFinanceiroPedido.NAO_PAGO,
                    deveAguardarPagamento ? TipoPagamentoPedido.PRE_PAGO : TipoPagamentoPedido.POS_PAGO,
                    deveAguardarPagamento
            );
        }

        if (!sessaoConfig.isEnabled()) {
            throw new BusinessException("Sessão de consumo está indisponível para este negócio.");
        }
        if (!sessaoConfig.isPermitirModoAnonimo()) {
            throw new BusinessException("Pedido público anônimo não está permitido para este negócio.");
        }
        if (mesa != null && !sessaoConfig.isPermitirSessaoComMesa()) {
            throw new BusinessException("Sessão com mesa não está permitida para este negócio.");
        }
        if (mesa == null && !sessaoConfig.isPermitirSessaoSemMesa()) {
            throw new BusinessException("Sessão sem mesa não está permitida para este negócio.");
        }

        TipoSessao tipoSessao = sessaoConfig.getTipoSessaoPadrao() != null ? sessaoConfig.getTipoSessaoPadrao() : TipoSessao.POS_PAGO;
        if (tipoSessao == TipoSessao.POS_PAGO && !sessaoConfig.isPermitirPosPago()) {
            throw new BusinessException("Sessão pós-paga não está permitida para este negócio.");
        }
        if (tipoSessao == TipoSessao.PRE_PAGO && !sessaoConfig.isPermitirPrePago()) {
            throw new BusinessException("Sessão pré-paga não está permitida para este negócio.");
        }
        if (tipoSessao == TipoSessao.PRE_PAGO && sessaoConfig.isExigirSaldoParaPedido()) {
            deveAguardarPagamento = true;
        }
        if (tipoSessao == TipoSessao.POS_PAGO
                && policy != null
                && policy.isPagamentoObrigatorioAntesDoPedido()
                && !policy.isPermitirPosPago()
                && !policy.isPermitirPedidoSemPagamento()
                && !policy.isPermitirPagamentoNaEntrega()) {
            deveAguardarPagamento = true;
        }

        return new DecisaoPedidoPublico(
                true,
                tipoSessao,
                deveAguardarPagamento ? StatusFinanceiroPedido.PENDENTE_PAGAMENTO : StatusFinanceiroPedido.NAO_PAGO,
                deveAguardarPagamento ? TipoPagamentoPedido.PRE_PAGO : TipoPagamentoPedido.POS_PAGO,
                deveAguardarPagamento
        );
    }

    private Map<Cozinha, List<PublicQrPedidoItemRequest>> agruparItensPorCozinha(
            Long unidadeAtendimentoId,
            List<PublicQrPedidoItemRequest> itens,
            List<Produto> produtos
    ) {
        Map<Cozinha, List<PublicQrPedidoItemRequest>> requestsPorCozinha = new HashMap<>();
        for (PublicQrPedidoItemRequest item : itens) {
            Produto produto = produtos.stream()
                    .filter(p -> p.getId().equals(item.getProdutoId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Produto inválido ou indisponível."));

            Cozinha cozinha = subPedidoService.determinarCozinha(produto, unidadeAtendimentoId);
            if (!Boolean.TRUE.equals(cozinha.getAtiva())) {
                throw new BusinessException("Produto inválido ou indisponível.");
            }
            requestsPorCozinha.computeIfAbsent(cozinha, k -> new ArrayList<>()).add(item);
        }
        return requestsPorCozinha;
    }

    private SessaoConsumo resolverOuCriarSessaoMinima(
            QrCodeOperacional qr,
            Instituicao instituicao,
            UnidadeAtendimento unidadeAtendimento,
            Mesa mesa,
            TipoSessao tipoSessao
    ) {
        Long tenantId = qr.getTenant() != null ? qr.getTenant().getId() : null;
        if (tenantId == null) {
            throw new BusinessException("QR sem tenant válido.");
        }
        return sessaoConsumoService.resolveOrCreateSessaoAnonima(
                tenantId,
                instituicao,
                unidadeAtendimento,
                qr.getTipo() == QrCodeOperacionalTipo.MESA ? mesa : null,
                tipoSessao != null ? tipoSessao : TipoSessao.POS_PAGO,
                false
        );
    }

    /**
     * Busca pedido público pelo QR token e ID do pedido.
     *
     * <p>Segurança:
     * <ul>
     *   <li>Valida o QR token — 404 se inválido.</li>
     *   <li>Resolve o tenant a partir do QR — tenant isolation garantido.</li>
     *   <li>Busca pedido apenas dentro do mesmo tenant — outro tenant → 404.</li>
     *   <li>Não exige JWT.</li>
     *   <li>Retorna DTO público mínimo sem dados sensíveis (sem evidências, custos, tokens internos).</li>
     * </ul>
     *
     * @param token     token público do QR (não enumerável)
     * @param pedidoId  ID do pedido retornado no POST de criação
     * @return DTO público com estado mínimo do pedido
     * @throws ResourceNotFoundException se token inválido ou pedido não pertence ao tenant
     */
    @Transactional(readOnly = true)
    public PublicQrPedidoResponse buscarPedidoPublicoPorQrToken(String token, Long pedidoId) {
        // 1. Validar QR e resolver tenant
        QrCodeOperacional qr = qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(token);
        Tenant tenant = qr.getTenant();

        // 2. Buscar pedido com tenant isolation
        Pedido pedido = pedidoRepository.findByIdAndTenantIdComSubPedidos(pedidoId, tenant.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", "id", pedidoId));

        // 3. Mapear para DTO público seguro (reutiliza mapPedidoToResponse existente)
        PublicQrPedidoResponse resp = mapPedidoToResponse(pedido);
        return resp;
    }

    private PublicQrPedidoResponse mapPedidoToResponse(Pedido pedido) {
        if (pedido == null) {
            throw new ResourceNotFoundException("Pedido", "id", null);
        }

        var workflow = pedidoWorkflowMetadataService.resolve(pedido);
        SessaoConsumo sessao = pedido.getSessaoConsumo();
        Instituicao inst = sessao != null ? sessao.getInstituicao() : null;
        UnidadeAtendimento ua = sessao != null ? (sessao.getUnidadeAtendimento() != null ? sessao.getUnidadeAtendimento()
                : (sessao.getMesa() != null ? sessao.getMesa().getUnidadeAtendimento() : null)) : null;
        Mesa mesa = sessao != null ? sessao.getMesa() : null;
        if (ua == null && pedido.getSubPedidos() != null && !pedido.getSubPedidos().isEmpty()) {
            ua = pedido.getSubPedidos().get(0).getUnidadeAtendimento();
            inst = ua != null ? ua.getInstituicao() : null;
        }

        List<PublicQrPedidoItemResponse> itens = pedido.getItens() != null
                ? pedido.getItens().stream()
                .map(i -> PublicQrPedidoItemResponse.builder()
                        .produtoId(i.getProduto() != null ? i.getProduto().getId() : null)
                        .nome(i.getProduto() != null ? i.getProduto().getNome() : null)
                        .quantidade(i.getQuantidade())
                        .precoUnitario(i.getPrecoUnitario())
                        .subtotal(i.getSubtotal())
                        .build())
                .toList()
                : List.of();

        return PublicQrPedidoResponse.builder()
                .pedidoId(pedido.getId())
                .numero(pedido.getNumero())
                .statusOperacional(pedido.getStatus())
                .statusFinanceiro(pedido.getStatusFinanceiro())
                .tenantNome(pedido.getTenant() != null ? pedido.getTenant().getNome() : null)
                .instituicaoNome(inst != null ? inst.getNome() : null)
                .unidadeAtendimentoNome(ua != null ? ua.getNome() : null)
                .mesaReferencia(mesa != null ? mesa.getReferencia() : null)
                .mesaNumero(mesa != null ? mesa.getNumero() : null)
                .clienteNome(workflow.clienteNome())
                .clienteTelefoneMascarado(workflow.clienteTelefoneMascarado())
                .metodoPagamento(workflow.metodoPagamento())
                .metodoPagamentoDetalhe(workflow.metodoPagamentoDetalhe())
                .motivoRejeicao(workflow.motivoRejeicao())
                .ordemPagamentoToken(workflow.ordemPagamentoToken())
                .ordemPagamentoStatus(workflow.ordemPagamentoStatus())
                .entidade(workflow.entidade())
                .referencia(workflow.referencia())
                .paymentUrl(workflow.paymentUrl())
                .criadoEm(pedido.getCreatedAt())
                .atualizadoEm(pedido.getUpdatedAt())
                .pagoEm(pedido.getPagoEm())
                .aceiteEm(workflow.aceiteEm())
                .rejeitadoEm(workflow.rejeitadoEm())
                .total(pedido.getTotal())
                .itens(itens)
                .mensagem(mensagemTracking(pedido, workflow))
                .build();
    }

    private String mensagemTracking(Pedido pedido, PedidoWorkflowMetadataService.PedidoWorkflowMetadata workflow) {
        if (pedido.getStatus() == StatusPedido.CANCELADO && workflow.motivoRejeicao() != null && !workflow.motivoRejeicao().isBlank()) {
            return "Pedido rejeitado: " + workflow.motivoRejeicao();
        }
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            return "Pedido cancelado";
        }
        if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO) {
            return "Pagamento confirmado";
        }
        if (workflow.ordemPagamentoToken() != null && pedido.getStatusFinanceiro() != StatusFinanceiroPedido.PAGO) {
            return "Pedido criado e aguardando aceite/pagamento manual";
        }
        if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.PENDENTE_PAGAMENTO) {
            return "Pedido criado e aguardando pagamento";
        }
        if (pedido.getStatus() == StatusPedido.EM_ANDAMENTO) {
            return "Pedido aceite pelo operador";
        }
        return "Pedido encontrado";
    }

    private record DecisaoPedidoPublico(
            boolean usarSessao,
            TipoSessao tipoSessao,
            StatusFinanceiroPedido statusFinanceiro,
            TipoPagamentoPedido tipoPagamento,
            boolean deveAguardarPagamento
    ) {
    }

    private record SelecaoMetodoPagamento(
            PaymentMethodCode code,
            com.restaurante.financeiro.enums.MetodoPagamentoAppyPay digitalMethod
    ) {
        boolean isManual() {
            return code == PaymentMethodCode.CASH || code == PaymentMethodCode.TPA;
        }
    }

    private record ClientePedidoIdentificado(
            com.restaurante.consumo.identificacao.entity.ClienteConsumo clienteConsumo,
            String telefoneNormalizado
    ) {
    }
}
