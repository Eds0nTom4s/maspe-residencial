package com.restaurante.service;

import com.restaurante.dto.request.PublicQrPedidoItemRequest;
import com.restaurante.dto.request.PublicQrPedidoRequest;
import com.restaurante.dto.response.PublicQrPedidoItemResponse;
import com.restaurante.dto.response.PublicQrPedidoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.service.OrdemPagamentoService;
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
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.PedidoOrigem;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TipoPagamentoPedido;
import com.restaurante.model.enums.TipoSessao;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.TenantCardapioConfigRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
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
    private final TenantCardapioConfigService tenantCardapioConfigService;
    private final TenantCardapioConfigRepository tenantCardapioConfigRepository;
    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final TenantPagamentoPolicyService tenantPagamentoPolicyService;
    private final TenantOperationalModulesService tenantOperationalModulesService;
    private final TenantSessaoConsumoConfigService tenantSessaoConsumoConfigService;
    private final OrdemPagamentoService ordemPagamentoService;

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
        if (qr.getTipo() == QrCodeOperacionalTipo.MESA && !modules.isQrMesaEnabled()) {
            throw new BusinessException("QR por mesa está indisponível para este negócio.");
        }
        var pagamentoPolicy = tenantPagamentoPolicyService.obterParaTenant(tenant.getId());
        TenantSessaoConsumoConfig sessaoConfig = tenantSessaoConsumoConfigService.obterParaTenant(tenant.getId());
        DecisaoPedidoPublico decisao = decidirFluxoPedidoPublico(qr, modules, sessaoConfig, pagamentoPolicy, mesa);
        validarLimiteItensPorPedido(tenant.getId(), request.getItens());

        SessaoConsumo sessao = decisao.usarSessao()
                ? resolverSessaoParaPedido(qr, instituicao, unidadeAtendimento, mesa, request.getSessaoConsumoId(), decisao.tipoSessao())
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
            pedido.setTurnoOperacional(turnoAberto);
            pedido.setStatus(StatusPedido.CRIADO);
            pedido.setPedidoOrigem(resolveOrigemDoQr(qr));
            pedido.setStatusFinanceiro(StatusFinanceiroPedido.NAO_PAGO);
            pedido.setTipoPagamento(TipoPagamentoPedido.POS_PAGO);
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
                pedido.getSubPedidos().add(subPedido);
            }

            pedido.calcularTotal();
            pedidoRepository.save(pedido);

            operationalEventLogService.logPedidoCriado(
                    pedido,
                    OperationalOrigem.QR_PUBLICO,
                    "Pedido público por QR criado",
                    Map.of(
                            "command", "CREATE_PUBLIC_QR_ORDER",
                            "pedidoOrigem", pedido.getPedidoOrigem() != null ? pedido.getPedidoOrigem().name() : "UNKNOWN",
                            "qrTipo", qr.getTipo() != null ? qr.getTipo().name() : "UNKNOWN"
                    ),
                    null,
                    null
            );

            if (sessao != null) {
                sessaoConsumoService.registrarAtividade(sessao, "Pedido público por QR criado: " + pedido.getNumero());
            }
            idempotencyService.markCompleted(idemReq, pedido);

            return PublicQrPedidoResponse.builder()
                    .pedidoId(pedido.getId())
                    .numero(pedido.getNumero())
                    .statusOperacional(pedido.getStatus())
                    .statusFinanceiro(pedido.getStatusFinanceiro())
                    .tenantNome(tenant.getNome())
                    .instituicaoNome(instituicao.getNome())
                    .unidadeAtendimentoNome(unidadeAtendimento.getNome())
                    .mesaReferencia(mesa != null ? mesa.getReferencia() : null)
                    .mesaNumero(mesa != null ? mesa.getNumero() : null)
                    .total(pedido.getTotal())
                    .itens(itensResponse)
                    .paymentOrder(ordemPagamentoService.buscarPaymentOrderResponseDoPedido(tenant.getId(), pedido.getId()))
                    .mensagem("Pedido enviado. Aguarde o aceite do estabelecimento para instruções de pagamento.")
                    .build();
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
                    StatusFinanceiroPedido.NAO_PAGO,
                    TipoPagamentoPedido.POS_PAGO,
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
                StatusFinanceiroPedido.NAO_PAGO,
                TipoPagamentoPedido.POS_PAGO,
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

    private SessaoConsumo resolverSessaoParaPedido(
            QrCodeOperacional qr,
            Instituicao instituicao,
            UnidadeAtendimento unidadeAtendimento,
            Mesa mesa,
            Long sessaoConsumoId,
            TipoSessao tipoSessao
    ) {
        if (sessaoConsumoId == null) {
            return resolverOuCriarSessaoMinima(qr, instituicao, unidadeAtendimento, mesa, tipoSessao);
        }

        Long tenantId = qr.getTenant() != null ? qr.getTenant().getId() : null;
        if (tenantId == null) {
            throw new BusinessException("QR sem tenant válido.");
        }

        SessaoConsumo sessao = sessaoConsumoRepository.findByIdAndTenantId(sessaoConsumoId, tenantId)
                .orElseThrow(() -> new BusinessException("SESSAO_CONSUMO_NOT_FOUND"));

        if (sessao.getStatus() != com.restaurante.model.enums.StatusSessaoConsumo.ABERTA) {
            throw new BusinessException("SESSAO_CONSUMO_NOT_ACTIVE");
        }

        return sessao;
    }

    private void validarLimiteItensPorPedido(Long tenantId, List<PublicQrPedidoItemRequest> itens) {
        if (itens == null || itens.isEmpty()) {
            return;
        }

        int quantidadeTotal = itens.stream()
                .mapToInt(item -> item.getQuantidade() != null ? item.getQuantidade() : 0)
                .sum();

        Integer limite = tenantCardapioConfigRepository.findByTenantId(tenantId)
                .map(config -> config.getMaxItensPorPedido())
                .orElse(null);

        if (limite != null && limite > 0 && quantidadeTotal > limite) {
            throw new BusinessException("LIMITE_ITENS_EXCEDIDO");
        }
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
        Pedido pedido = pedidoRepository.findByIdAndTenantIdComItens(pedidoId, tenant.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", "id", pedidoId));

        // 3. Mapear para DTO público seguro (reutiliza mapPedidoToResponse existente)
        PublicQrPedidoResponse resp = mapPedidoToResponse(pedido);
        resp.setMensagem("Pedido encontrado");
        return resp;
    }

    private PublicQrPedidoResponse mapPedidoToResponse(Pedido pedido) {
        if (pedido == null) {
            throw new ResourceNotFoundException("Pedido", "id", null);
        }

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
                .total(pedido.getTotal())
                .itens(itens)
                .paymentOrder(pedido.getTenant() != null
                        ? ordemPagamentoService.buscarPaymentOrderResponseDoPedido(pedido.getTenant().getId(), pedido.getId())
                        : null)
                .mensagem("Pedido já criado anteriormente")
                .build();
    }

    private PedidoOrigem resolveOrigemDoQr(QrCodeOperacional qr) {
        if (qr == null || qr.getTipo() == null) {
            return PedidoOrigem.QR_PUBLICO;
        }
        if (qr.getTipo() == QrCodeOperacionalTipo.MESA) {
            return PedidoOrigem.QR_MESA;
        }
        // QR geral do tenant/instituição/unidade/balcão/etc é tratado como QR principal/menu público.
        return PedidoOrigem.QR_PRINCIPAL;
    }

    private record DecisaoPedidoPublico(
            boolean usarSessao,
            TipoSessao tipoSessao,
            StatusFinanceiroPedido statusFinanceiro,
            TipoPagamentoPedido tipoPagamento,
            boolean deveAguardarPagamento
    ) {
    }
}
