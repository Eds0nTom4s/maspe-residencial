package com.restaurante.service.tenantadmin;

import com.restaurante.dto.request.TenantPdvCreatePedidoItemRequest;
import com.restaurante.dto.request.TenantPdvCreatePedidoRequest;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.service.OrdemPagamentoService;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantPdvOrderIdempotencyRecord;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.UnidadeProducao;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.PedidoOrigem;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantPdvOrderIdempotencyStatus;
import com.restaurante.model.enums.TipoPagamentoPedido;
import com.restaurante.model.enums.TipoSessao;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantPdvOrderIdempotencyRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.PedidoNumberService;
import com.restaurante.service.SessaoConsumoService;
import com.restaurante.service.SubPedidoService;
import com.restaurante.service.operacional.OperationalCapabilitiesPolicy;
import com.restaurante.service.operacional.OperationalEventLogService;
import com.restaurante.service.producao.RotaProducaoService;
import com.restaurante.service.producao.UnidadeProducaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantPdvPedidoService {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final TurnoOperacionalRepository turnoOperacionalRepository;
    private final ProdutoRepository produtoRepository;
    private final PedidoRepository pedidoRepository;
    private final TenantPdvOrderIdempotencyRepository idempotencyRepository;
    private final PedidoNumberService pedidoNumberService;
    private final SessaoConsumoService sessaoConsumoService;
    private final SubPedidoService subPedidoService;
    private final RotaProducaoService rotaProducaoService;
    private final UnidadeProducaoService unidadeProducaoService;
    private final OperationalCapabilitiesPolicy operationalCapabilitiesPolicy;
    private final OperationalEventLogService operationalEventLogService;
    private final OrdemPagamentoService ordemPagamentoService;

    @Transactional
    public CreateResult criarPedido(
            TenantPdvCreatePedidoRequest request,
            String idempotencyKey,
            OperationalOrigem actor,
            String ip,
            String userAgent
    ) {
        TenantContext context = requireContext();
        validateCommand(request, idempotencyKey);

        User user = userRepository.findByIdForUpdate(context.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        String normalizedKey = idempotencyKey.trim();
        String normalizedClientRequestId = request.getClientRequestId().trim();
        String requestHash = requestHash(context.tenantId(), context.userId(), request);

        TenantPdvOrderIdempotencyRecord idempotency = findExisting(
                context.tenantId(), context.userId(), normalizedKey, normalizedClientRequestId
        );
        if (idempotency != null) {
            validateReplay(idempotency, requestHash);
            return new CreateResult(idempotency.getPedido().getId(), true);
        }

        Tenant tenant = tenantRepository.findById(context.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        Instituicao instituicao = instituicaoRepository.findByIdAndTenantId(request.getInstituicaoId(), context.tenantId())
                .filter(item -> Boolean.TRUE.equals(item.getAtiva()))
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        UnidadeAtendimento unidade = unidadeAtendimentoRepository.findByIdAndTenantId(
                        request.getUnidadeAtendimentoId(), context.tenantId())
                .filter(item -> Boolean.TRUE.equals(item.getAtiva()))
                .filter(item -> item.getInstituicao() != null && instituicao.getId().equals(item.getInstituicao().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        TurnoOperacional turno = turnoOperacionalRepository.findOpenByTenantAndInstituicaoAndUnidade(
                        context.tenantId(),
                        instituicao.getId(),
                        unidade.getId(),
                        List.of(TurnoOperacionalStatus.ABERTO)
                )
                .orElseThrow(() -> new ConflictException("Turno operacional ABERTO é obrigatório para venda PDV."));

        Map<Long, Produto> produtos = loadProducts(context.tenantId(), request.getItens());
        TenantPdvOrderIdempotencyRecord createdIdempotency = new TenantPdvOrderIdempotencyRecord();
        createdIdempotency.setTenant(tenant);
        createdIdempotency.setUser(user);
        createdIdempotency.setIdempotencyKey(normalizedKey);
        createdIdempotency.setClientRequestId(normalizedClientRequestId);
        createdIdempotency.setRequestHash(requestHash);
        createdIdempotency.setStatus(TenantPdvOrderIdempotencyStatus.IN_PROGRESS);
        createdIdempotency = idempotencyRepository.save(createdIdempotency);

        SessaoConsumo sessao = sessaoConsumoService.resolveOrCreateSessaoAnonima(
                context.tenantId(), instituicao, unidade, null, TipoSessao.POS_PAGO, false
        );
        Pedido pedido = buildPedido(tenant, turno, sessao, request);
        boolean productionEnabled = operationalCapabilitiesPolicy.resolve(tenant).productionEnabled();
        addItemsAndProduction(pedido, tenant, instituicao, unidade, request.getItens(), produtos, productionEnabled);
        pedido.calcularTotal();
        pedido = pedidoRepository.saveAndFlush(pedido);

        OperationalOrigem effectiveActor = actor != null ? actor : OperationalOrigem.TENANT_CASHIER;
        ordemPagamentoService.criarOrdemPagamentoPedido(
                tenant,
                instituicao,
                unidade,
                null,
                turno,
                pedido,
                request.getMetodoPagamento(),
                effectiveActor,
                ip,
                userAgent
        );

        operationalEventLogService.logPedidoCriado(
                pedido,
                effectiveActor,
                "Pedido criado pelo PDV web",
                Map.of(
                        "command", "CREATE_TENANT_PDV_ORDER",
                        "clientRequestId", normalizedClientRequestId,
                        "pedidoOrigem", PedidoOrigem.PDV_INTERNO.name(),
                        "instituicaoId", instituicao.getId(),
                        "unidadeAtendimentoId", unidade.getId(),
                        "turnoId", turno.getId(),
                        "metodoPagamento", request.getMetodoPagamento().name()
                ),
                ip,
                userAgent
        );

        createdIdempotency.setPedido(pedido);
        createdIdempotency.setStatus(TenantPdvOrderIdempotencyStatus.COMPLETED);
        idempotencyRepository.save(createdIdempotency);
        return new CreateResult(pedido.getId(), false);
    }

    private TenantContext requireContext() {
        TenantContext context = tenantGuard.requireContext();
        if (context.tenantId() == null || context.userId() == null) {
            throw new ResourceNotFoundException("Recurso não encontrado.");
        }
        tenantGuard.assertCurrentUserBelongsToTenant(context.tenantId());
        tenantGuard.assertTenantActive(context.tenantId());
        return context;
    }

    private void validateCommand(TenantPdvCreatePedidoRequest request, String idempotencyKey) {
        if (request == null) throw new BusinessException("Pedido PDV é obrigatório.");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.trim().length() > 120) {
            throw new BusinessException("Idempotency-Key é obrigatório e deve possuir no máximo 120 caracteres.");
        }
        if (request.getMetodoPagamento() == null || request.getMetodoPagamento() == MetodoPagamentoManual.APPYPAY) {
            throw new BusinessException("Método inválido para venda PDV. Use CASH ou TPA.");
        }
        if (request.getClientRequestId() == null || request.getClientRequestId().isBlank()) {
            throw new BusinessException("clientRequestId é obrigatório.");
        }
        if (request.getItens() == null || request.getItens().isEmpty()) {
            throw new BusinessException("Itens do pedido são obrigatórios.");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (TenantPdvCreatePedidoItemRequest item : request.getItens()) {
            if (item == null || item.getProdutoId() == null || !ids.add(item.getProdutoId())) {
                throw new BusinessException("Cada produto deve aparecer uma única vez no pedido.");
            }
            if (item.getQuantidade() == null || item.getQuantidade() < 1) {
                throw new BusinessException("Quantidade inválida para produto do pedido.");
            }
        }
    }

    private Map<Long, Produto> loadProducts(Long tenantId, List<TenantPdvCreatePedidoItemRequest> items) {
        List<Long> ids = items.stream().map(TenantPdvCreatePedidoItemRequest::getProdutoId).toList();
        Map<Long, Produto> products = produtoRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(Produto::getId, Function.identity()));
        if (products.size() != ids.size()) {
            throw new ResourceNotFoundException("Recurso não encontrado.");
        }
        for (TenantPdvCreatePedidoItemRequest item : items) {
            Produto product = products.get(item.getProdutoId());
            if (product == null
                    || !Boolean.TRUE.equals(product.getAtivo())
                    || !Boolean.TRUE.equals(product.getDisponivel())
                    || product.getPreco() == null
                    || product.getPreco().signum() < 0
                    || product.getCategoriaProduto() == null
                    || product.getCategoriaProduto().getTenant() == null
                    || !tenantId.equals(product.getCategoriaProduto().getTenant().getId())
                    || !Boolean.TRUE.equals(product.getCategoriaProduto().getAtivo())) {
                throw new ConflictException("Produto inválido ou indisponível para venda.");
            }
        }
        return products;
    }

    private Pedido buildPedido(
            Tenant tenant,
            TurnoOperacional turno,
            SessaoConsumo sessao,
            TenantPdvCreatePedidoRequest request
    ) {
        Pedido pedido = new Pedido();
        pedido.setTenant(tenant);
        pedido.setNumero(pedidoNumberService.gerarNumeroPedido(tenant.getId()));
        pedido.setSessaoConsumo(sessao);
        pedido.setTurnoOperacional(turno);
        pedido.setStatus(StatusPedido.CRIADO);
        pedido.setPedidoOrigem(PedidoOrigem.PDV_INTERNO);
        pedido.setStatusFinanceiro(StatusFinanceiroPedido.NAO_PAGO);
        pedido.setTipoPagamento(TipoPagamentoPedido.POS_PAGO);
        pedido.setObservacoes(trim(request.getObservacao()));
        return pedido;
    }

    private void addItemsAndProduction(
            Pedido pedido,
            Tenant tenant,
            Instituicao instituicao,
            UnidadeAtendimento unidade,
            List<TenantPdvCreatePedidoItemRequest> items,
            Map<Long, Produto> products,
            boolean productionEnabled
    ) {
        if (!productionEnabled) {
            for (TenantPdvCreatePedidoItemRequest requestItem : items) {
                pedido.adicionarItem(buildItem(pedido, null, tenant, products.get(requestItem.getProdutoId()), requestItem));
            }
            return;
        }

        Map<Cozinha, List<TenantPdvCreatePedidoItemRequest>> byKitchen = new LinkedHashMap<>();
        for (TenantPdvCreatePedidoItemRequest requestItem : items) {
            Produto product = products.get(requestItem.getProdutoId());
            Cozinha kitchen = subPedidoService.determinarCozinha(product, unidade.getId());
            if (!Boolean.TRUE.equals(kitchen.getAtiva())) {
                throw new ConflictException("Produto não possui cozinha ativa para produção.");
            }
            byKitchen.computeIfAbsent(kitchen, ignored -> new ArrayList<>()).add(requestItem);
        }

        int index = 1;
        for (Map.Entry<Cozinha, List<TenantPdvCreatePedidoItemRequest>> entry : byKitchen.entrySet()) {
            UnidadeProducao productionUnit = resolveProductionUnit(
                    tenant.getId(), instituicao.getId(), entry.getValue(), products
            );
            SubPedido subPedido = SubPedido.builder()
                    .numero(pedido.getNumero() + "-" + index++)
                    .pedido(pedido)
                    .cozinha(entry.getKey())
                    .unidadeAtendimento(unidade)
                    .status(StatusSubPedido.CRIADO)
                    .build();
            subPedido.setTenant(tenant);
            subPedido.setUnidadeProducao(productionUnit);
            for (TenantPdvCreatePedidoItemRequest requestItem : entry.getValue()) {
                ItemPedido item = buildItem(pedido, subPedido, tenant, products.get(requestItem.getProdutoId()), requestItem);
                pedido.adicionarItem(item);
                subPedido.adicionarItem(item);
            }
            subPedido.calcularTotal();
            pedido.getSubPedidos().add(subPedido);
        }
    }

    private UnidadeProducao resolveProductionUnit(
            Long tenantId,
            Long instituicaoId,
            List<TenantPdvCreatePedidoItemRequest> items,
            Map<Long, Produto> products
    ) {
        UnidadeProducao resolved = null;
        for (TenantPdvCreatePedidoItemRequest item : items) {
            Produto product = products.get(item.getProdutoId());
            UnidadeProducao candidate = rotaProducaoService.resolverUnidadeProducaoParaCategoria(
                    tenantId, instituicaoId, product.getCategoriaProduto().getId()
            );
            if (resolved == null) {
                resolved = candidate;
            } else if (!resolved.getId().equals(candidate.getId())) {
                return unidadeProducaoService.obterDefaultParaInstituicao(tenantId, instituicaoId);
            }
        }
        return resolved;
    }

    private ItemPedido buildItem(
            Pedido pedido,
            SubPedido subPedido,
            Tenant tenant,
            Produto product,
            TenantPdvCreatePedidoItemRequest request
    ) {
        ItemPedido item = ItemPedido.builder()
                .pedido(pedido)
                .subPedido(subPedido)
                .produto(product)
                .quantidade(request.getQuantidade())
                .precoUnitario(product.getPreco())
                .observacoes(trim(request.getObservacao()))
                .build();
        item.setTenant(tenant);
        item.calcularSubtotal();
        return item;
    }

    private TenantPdvOrderIdempotencyRecord findExisting(
            Long tenantId,
            Long userId,
            String idempotencyKey,
            String clientRequestId
    ) {
        return idempotencyRepository.findByTenantIdAndUserIdAndIdempotencyKey(tenantId, userId, idempotencyKey)
                .orElseGet(() -> idempotencyRepository
                        .findByTenantIdAndUserIdAndClientRequestId(tenantId, userId, clientRequestId)
                        .orElse(null));
    }

    private void validateReplay(TenantPdvOrderIdempotencyRecord record, String requestHash) {
        if (!requestHash.equals(record.getRequestHash())) {
            throw new ConflictException("Conflito de idempotência: chave reutilizada com payload diferente.");
        }
        if (record.getStatus() != TenantPdvOrderIdempotencyStatus.COMPLETED || record.getPedido() == null) {
            throw new ConflictException("Pedido PDV já está em processamento.");
        }
    }

    private String requestHash(Long tenantId, Long userId, TenantPdvCreatePedidoRequest request) {
        try {
            StringBuilder canonical = new StringBuilder()
                    .append(tenantId).append('|')
                    .append(userId).append('|')
                    .append(request.getInstituicaoId()).append('|')
                    .append(request.getUnidadeAtendimentoId()).append('|')
                    .append(request.getMetodoPagamento()).append('|')
                    .append(request.getClientRequestId().trim()).append('|')
                    .append(trim(request.getObservacao())).append('|');
            request.getItens().stream()
                    .sorted(Comparator.comparing(TenantPdvCreatePedidoItemRequest::getProdutoId))
                    .forEach(item -> canonical
                            .append(item.getProdutoId()).append(':')
                            .append(item.getQuantidade()).append(':')
                            .append(trim(item.getObservacao())).append(';'));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Falha ao calcular hash de idempotência.", exception);
        }
    }

    private String trim(String value) {
        if (value == null) return "";
        return value.trim();
    }

    public record CreateResult(Long pedidoId, boolean idempotentReplay) {
    }
}
