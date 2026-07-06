package com.restaurante.service.device;

import com.restaurante.config.DeviceOrdersProperties;
import com.restaurante.config.OperacaoProperties;
import com.restaurante.dto.request.DeviceCriarPedidoItemRequest;
import com.restaurante.dto.request.DeviceCriarPedidoRequest;
import com.restaurante.dto.response.DevicePedidoItemResponse;
import com.restaurante.dto.response.DevicePedidoResponse;
import com.restaurante.dto.response.DeviceSubPedidoResponse;
import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.DeviceApiException;
import com.restaurante.exception.DeviceForbiddenException;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.DevicePedidoIdempotencyRecord;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DevicePedidoIdempotencyStatus;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.PedidoOrigem;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TipoPagamentoPedido;
import com.restaurante.model.enums.TipoSessao;
import com.restaurante.repository.DevicePedidoIdempotencyRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.PedidoNumberService;
import com.restaurante.service.SessaoConsumoService;
import com.restaurante.service.SubPedidoService;
import com.restaurante.service.operacional.OperationalEventLogService;
import com.restaurante.service.producao.RotaProducaoService;
import com.restaurante.service.producao.UnidadeProducaoService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DevicePedidoService {

    @PersistenceContext
    private EntityManager entityManager;

    private final DeviceOrdersProperties deviceOrdersProperties;
    private final OperacaoProperties operacaoProperties;

    private final TenantRepository tenantRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final MesaRepository mesaRepository;
    private final QrCodeOperacionalRepository qrCodeOperacionalRepository;
    private final ProdutoRepository produtoRepository;
    private final PedidoRepository pedidoRepository;
    private final SubPedidoRepository subPedidoRepository;
    private final DevicePedidoIdempotencyRepository idempotencyRepository;
    private final TurnoOperacionalRepository turnoOperacionalRepository;

    private final PedidoNumberService pedidoNumberService;
    private final SessaoConsumoService sessaoConsumoService;
    private final SubPedidoService subPedidoService;
    private final RotaProducaoService rotaProducaoService;
    private final UnidadeProducaoService unidadeProducaoService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public DevicePedidoResponse criarPedido(DeviceCriarPedidoRequest request, String idempotencyKey, String userAgent, String ip) {
        DevicePrincipal device = requireDevicePrincipal();
        requireCapability(device, DeviceCapability.CREATE_ORDER);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_IDEMPOTENCY_KEY_REQUIRED,
                    "Idempotency-Key é obrigatório.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    null);
        }
        if (request == null || request.getClientRequestId() == null || request.getClientRequestId().isBlank()) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_CLIENT_REQUEST_ID_REQUIRED,
                    "clientRequestId é obrigatório.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    null);
        }
        if (request.getItens() == null || request.getItens().isEmpty()) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_EMPTY_ITEMS,
                    "Itens do pedido são obrigatórios.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    null);
        }
        if (request.getItens().size() > deviceOrdersProperties.getMaxItems()) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_VALIDATION_FAILED,
                    "Número máximo de itens excedido.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    Map.of("maxItems", deviceOrdersProperties.getMaxItems()));
        }
        if (request.getObservacao() != null && request.getObservacao().length() > deviceOrdersProperties.getMaxObservacaoLength()) {
            throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_VALIDATION_FAILED,
                    "Observação muito longa.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    Map.of("maxObservacaoLength", deviceOrdersProperties.getMaxObservacaoLength()));
        }

        Long tenantId = device.tenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new DeviceApiException(
                HttpStatus.NOT_FOUND,
                DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                "Recurso não encontrado.",
                false,
                DeviceErrorResponse.DeviceRecoveryAction.NONE,
                null
        ));
        Instituicao inst = instituicaoRepository.findById(device.instituicaoId())
                .filter(i -> i.getTenant() != null && i.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new DeviceApiException(
                        HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Recurso não encontrado.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null
                ));
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(device.unidadeAtendimentoId())
                .filter(u -> u.getInstituicao() != null && u.getInstituicao().getTenant() != null && u.getInstituicao().getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new DeviceApiException(
                        HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Recurso não encontrado.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null
                ));

        DispositivoOperacional dispositivo = dispositivoOperacionalRepository.findByIdAndTenantId(device.dispositivoId(), tenantId)
                .orElseThrow(() -> new DeviceUnauthorizedException("Dispositivo inválido."));

        String requestHash = computeRequestHash(request);

        DevicePedidoIdempotencyRecordState idem = beginOrReplayIdempotency(tenant, dispositivo, idempotencyKey.trim(), request.getClientRequestId().trim(), requestHash);
        if (idem.replayResponse != null) {
            return idem.replayResponse;
        }

        TurnoOperacional turno = turnoOperacionalRepository
                .findOpenByTenantAndInstituicaoAndUnidade(tenantId, inst.getId(), ua.getId())
                .orElse(null);
        if (turno == null && operacaoProperties.isRequireOpenTurnoForDeviceOrders()) {
            failIdempotency(idem.record, DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_TURNO_REQUIRED.name());
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_TURNO_REQUIRED,
                    "Turno operacional aberto é obrigatório para pedidos do POS.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                    null);
        }

        Mesa mesa = null;
        if (request.getMesaId() != null) {
            mesa = mesaRepository.findByIdAndTenantId(request.getMesaId(), tenantId)
                    .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                            DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_MESA_NOT_FOUND,
                            "Mesa não encontrada.",
                            false,
                            DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                            null));
            if (mesa.getUnidadeAtendimento() == null || !mesa.getUnidadeAtendimento().getId().equals(ua.getId())) {
                throw new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_MESA_SCOPE_INVALID,
                        "Mesa não pertence à unidade do dispositivo.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                        null);
            }
        }

        if (request.getQrCodeId() != null) {
            QrCodeOperacional qr = qrCodeOperacionalRepository.findByIdAndTenantId(request.getQrCodeId(), tenantId)
                    .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                            DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_QR_INVALID,
                            "QR inválido.",
                            false,
                            DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                            null));
            if (!Boolean.TRUE.equals(qr.getAtivo()) || Boolean.TRUE.equals(qr.getRevogado())) {
                throw new DeviceApiException(HttpStatus.CONFLICT,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_QR_INVALID,
                        "QR revogado/inativo.",
                        true,
                        DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                        null);
            }
            if (qr.getUnidadeAtendimento() == null || !qr.getUnidadeAtendimento().getId().equals(ua.getId())) {
                throw new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_QR_INVALID,
                        "QR não pertence à unidade do dispositivo.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                        null);
            }
        }

        List<Long> produtoIds = request.getItens().stream().map(DeviceCriarPedidoItemRequest::getProdutoId).distinct().toList();
        List<Produto> produtos = produtoRepository.findByTenantIdAndIdIn(tenantId, produtoIds);
        if (produtos.size() != produtoIds.size()) {
            failIdempotency(idem.record, DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_PRODUCT_NOT_FOUND.name());
            throw new DeviceApiException(HttpStatus.NOT_FOUND,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_PRODUCT_NOT_FOUND,
                    "Produto inválido ou inexistente.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                    null);
        }
        Map<Long, Produto> porId = produtos.stream().collect(Collectors.toMap(Produto::getId, p -> p));
        for (DeviceCriarPedidoItemRequest it : request.getItens()) {
            if (it.getQuantidade() == null || it.getQuantidade() <= 0) {
                throw new DeviceApiException(HttpStatus.BAD_REQUEST,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_INVALID_QUANTITY,
                        "Quantidade inválida.",
                        true,
                        DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                        null);
            }
            Produto p = porId.get(it.getProdutoId());
            if (p == null || !Boolean.TRUE.equals(p.getAtivo()) || !Boolean.TRUE.equals(p.getDisponivel())) {
                failIdempotency(idem.record, DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_PRODUCT_UNAVAILABLE.name());
                throw new DeviceApiException(HttpStatus.CONFLICT,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_PRODUCT_UNAVAILABLE,
                        "Produto inválido ou indisponível.",
                        true,
                        DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                        Map.of("produtoId", it.getProdutoId()));
            }
        }

        SessaoConsumo sessao = resolverOuCriarSessaoMinima(tenant, inst, ua, mesa);

        try {
            Pedido pedido = new Pedido();
            pedido.setTenant(tenant);
            pedido.setNumero(pedidoNumberService.gerarNumeroPedido(tenantId));
            pedido.setSessaoConsumo(sessao);
            pedido.setTurnoOperacional(turno);
            pedido.setStatus(StatusPedido.CRIADO);
            pedido.setPedidoOrigem(PedidoOrigem.DEVICE_POS);
            pedido.setStatusFinanceiro(StatusFinanceiroPedido.NAO_PAGO);
            pedido.setTipoPagamento(TipoPagamentoPedido.POS_PAGO);
            pedido.setObservacoes(trimObs(request.getObservacao()));

            Map<Cozinha, List<DeviceCriarPedidoItemRequest>> porCozinha = agruparItensPorCozinha(ua.getId(), request.getItens(), porId);

            pedido = pedidoRepository.save(pedido);

            List<DevicePedidoItemResponse> itensResp = new ArrayList<>();
            List<DeviceSubPedidoResponse> subResp = new ArrayList<>();
            int contadorSubPedido = 1;

            for (Map.Entry<Cozinha, List<DeviceCriarPedidoItemRequest>> entry : porCozinha.entrySet()) {
                Cozinha cozinha = entry.getKey();
                List<DeviceCriarPedidoItemRequest> itensReq = entry.getValue();

                SubPedido subPedido = SubPedido.builder()
                        .numero(pedido.getNumero() + "-" + contadorSubPedido)
                        .pedido(pedido)
                        .cozinha(cozinha)
                        .unidadeAtendimento(ua)
                        .status(StatusSubPedido.CRIADO)
                        .build();
                subPedido.setTenant(tenant);

                com.restaurante.model.entity.UnidadeProducao unidadeProducao = null;
                for (DeviceCriarPedidoItemRequest itemReq : itensReq) {
                    Produto prod = porId.get(itemReq.getProdutoId());
                    if (prod == null || prod.getCategoriaProduto() == null) {
                        throw new DeviceApiException(HttpStatus.CONFLICT,
                                DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_PRODUCT_UNAVAILABLE,
                                "Produto inválido ou indisponível.",
                                true,
                                DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                                Map.of("produtoId", itemReq.getProdutoId()));
                    }
                    var resolved = rotaProducaoService.resolverUnidadeProducaoParaCategoria(
                            tenantId, inst.getId(), prod.getCategoriaProduto().getId()
                    );
                    if (unidadeProducao == null) {
                        unidadeProducao = resolved;
                    } else if (!unidadeProducao.getId().equals(resolved.getId())) {
                        unidadeProducao = unidadeProducaoService.obterDefaultParaInstituicao(tenantId, inst.getId());
                        break;
                    }
                }
                subPedido.setUnidadeProducao(unidadeProducao);
                contadorSubPedido++;

                List<DevicePedidoItemResponse> itensSub = new ArrayList<>();
                for (DeviceCriarPedidoItemRequest itemReq : itensReq) {
                    Produto prod = porId.get(itemReq.getProdutoId());
                    ItemPedido item = ItemPedido.builder()
                            .pedido(pedido)
                            .subPedido(subPedido)
                            .produto(prod)
                            .quantidade(itemReq.getQuantidade())
                            .precoUnitario(prod.getPreco())
                            .observacoes(trimObs(itemReq.getObservacao()))
                            .build();
                    item.setTenant(tenant);
                    item.calcularSubtotal();

                    pedido.adicionarItem(item);
                    subPedido.adicionarItem(item);

                    DevicePedidoItemResponse ir = new DevicePedidoItemResponse();
                    ir.setItemPedidoId(item.getId()); // após persist, mas já útil para placeholder
                    ir.setProdutoId(prod.getId());
                    ir.setProdutoNome(prod.getNome());
                    ir.setQuantidade(itemReq.getQuantidade());
                    ir.setPrecoUnitario(prod.getPreco());
                    ir.setSubtotal(item.getSubtotal());
                    ir.setObservacao(itemReq.getObservacao());
                    itensResp.add(ir);
                    itensSub.add(ir);
                }

                subPedido.calcularTotal();
                pedido.getSubPedidos().add(subPedido);

                DeviceSubPedidoResponse sr = new DeviceSubPedidoResponse();
                sr.setSubPedidoId(subPedido.getId());
                sr.setUnidadeProducaoId(unidadeProducao != null ? unidadeProducao.getId() : null);
                sr.setUnidadeProducaoNome(unidadeProducao != null ? unidadeProducao.getNome() : null);
                sr.setStatus(subPedido.getStatus());
                sr.setItens(itensSub);
                subResp.add(sr);
            }

            pedido.calcularTotal();
            pedidoRepository.save(pedido);

            completeIdempotency(idem.record, pedido);

            if (turno == null) {
                operationalEventLogService.logPedidoSemTurnoAberto(
                        pedido,
                        OperationalOrigem.DEVICE_POS,
                        "Pedido criado por device sem turno aberto",
                        Map.of("deviceId", device.dispositivoId(), "unidadeAtendimentoId", ua.getId()),
                        ip,
                        userAgent
                );
            }

            operationalEventLogService.logPedidoCriadoDevice(
                    pedido,
                    turno,
                    OperationalOrigem.DEVICE_POS,
                    "Pedido criado pelo POS",
                    Map.of(
                            "deviceId", device.dispositivoId(),
                            "clientRequestId", request.getClientRequestId(),
                            "pedidoOrigem", pedido.getPedidoOrigem() != null ? pedido.getPedidoOrigem().name() : "UNKNOWN"
                    ),
                    ip,
                    userAgent
            );

            return toResponse(pedido, itensResp, subResp, false);
        } catch (RuntimeException ex) {
            failIdempotency(idem.record, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public DevicePedidoResponse buscarPedido(Long pedidoId) {
        DevicePrincipal device = requireDevicePrincipal();
        requireCapability(device, DeviceCapability.VIEW_ORDERS);
        Pedido pedido = pedidoRepository.findByIdAndTenantId(pedidoId, device.tenantId())
                .orElseThrow(() -> new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                        "Pedido não encontrado.",
                        false,
                        DeviceErrorResponse.DeviceRecoveryAction.NONE,
                        null));
        if (pedido.getSessaoConsumo() == null || pedido.getSessaoConsumo().getUnidadeAtendimento() == null
                || !pedido.getSessaoConsumo().getUnidadeAtendimento().getId().equals(device.unidadeAtendimentoId())) {
            throw new DeviceApiException(HttpStatus.NOT_FOUND,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_REQUEST_INVALID,
                    "Pedido não encontrado.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }
        // DTO mínimo (sem recarregar coleção com fetch join nesta fase)
        return toResponse(pedido, List.of(), List.of(), false);
    }

    private DevicePedidoResponse toResponse(Pedido pedido, List<DevicePedidoItemResponse> itens, List<DeviceSubPedidoResponse> sub, boolean replay) {
        DevicePedidoResponse r = new DevicePedidoResponse();
        r.setPedidoId(pedido.getId());
        r.setNumeroPedido(pedido.getNumero());
        r.setStatusOperacional(pedido.getStatus());
        r.setStatusFinanceiro(pedido.getStatusFinanceiro());
        r.setTenantId(pedido.getTenant() != null ? pedido.getTenant().getId() : null);
        Long instId = pedido.getSessaoConsumo() != null && pedido.getSessaoConsumo().getInstituicao() != null ? pedido.getSessaoConsumo().getInstituicao().getId() : null;
        Long uaId = pedido.getSessaoConsumo() != null && pedido.getSessaoConsumo().getUnidadeAtendimento() != null ? pedido.getSessaoConsumo().getUnidadeAtendimento().getId() : null;
        Long mesaId = pedido.getSessaoConsumo() != null && pedido.getSessaoConsumo().getMesa() != null ? pedido.getSessaoConsumo().getMesa().getId() : null;
        r.setInstituicaoId(instId);
        r.setUnidadeAtendimentoId(uaId);
        r.setMesaId(mesaId);
        r.setTurnoOperacionalId(pedido.getTurnoOperacional() != null ? pedido.getTurnoOperacional().getId() : null);
        r.setTotal(pedido.getTotal());
        r.setCriadoEm(pedido.getCreatedAt());
        r.setItens(itens);
        r.setSubPedidos(sub);
        r.setIdempotentReplay(replay);
        return r;
    }

    private record DevicePedidoIdempotencyRecordState(DevicePedidoIdempotencyRecord record, DevicePedidoResponse replayResponse) {}

    private DevicePedidoIdempotencyRecordState beginOrReplayIdempotency(Tenant tenant, DispositivoOperacional dispositivo, String idemKey, String clientRequestId, String requestHash) {
        Long tenantId = tenant.getId();
        Long deviceId = dispositivo.getId();

        var byKey = idempotencyRepository.findByTenantIdAndDispositivoIdAndIdempotencyKey(tenantId, deviceId, idemKey);
        if (byKey.isPresent()) {
            return handleExistingIdem(byKey.get(), requestHash);
        }
        var byClient = idempotencyRepository.findByTenantIdAndDispositivoIdAndClientRequestId(tenantId, deviceId, clientRequestId);
        if (byClient.isPresent()) {
            return handleExistingIdem(byClient.get(), requestHash);
        }

        DevicePedidoIdempotencyRecord rec = new DevicePedidoIdempotencyRecord();
        rec.setTenant(tenant);
        rec.setDispositivo(dispositivo);
        rec.setIdempotencyKey(idemKey);
        rec.setClientRequestId(clientRequestId);
        rec.setRequestHash(requestHash);
        rec.setStatus(DevicePedidoIdempotencyStatus.IN_PROGRESS);

        try {
            idempotencyRepository.saveAndFlush(rec);
            return new DevicePedidoIdempotencyRecordState(rec, null);
        } catch (DataIntegrityViolationException ex) {
            // Em corrida de idempotência, a tentativa de INSERT pode falhar com violação de integridade.
            // Após essa exceção, o PersistenceContext pode conter entidades em estado inválido; limpamos
            // para evitar auto-flush/AssertionFailure em queries de retry.
            if (entityManager != null) {
                entityManager.clear();
            }
            // corrida: recarrega e aplica regra
            var retry = idempotencyRepository.findByTenantIdAndDispositivoIdAndIdempotencyKey(tenantId, deviceId, idemKey)
                    .or(() -> idempotencyRepository.findByTenantIdAndDispositivoIdAndClientRequestId(tenantId, deviceId, clientRequestId))
                    .orElseThrow(() -> ex);
            return handleExistingIdem(retry, requestHash);
        }
    }

    private DevicePedidoIdempotencyRecordState handleExistingIdem(DevicePedidoIdempotencyRecord rec, String requestHash) {
        if (!requestHash.equals(rec.getRequestHash())) {
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_IDEMPOTENCY_CONFLICT,
                    "Conflito de idempotência: mesma chave/requestId com payload diferente.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                    null);
        }
        if (rec.getStatus() == DevicePedidoIdempotencyStatus.COMPLETED && rec.getPedido() != null) {
            Pedido pedido = pedidoRepository.findById(rec.getPedido().getId())
                    .orElseThrow(() -> new DeviceApiException(HttpStatus.CONFLICT,
                            DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_IDEMPOTENCY_CONFLICT,
                            "Registro idempotente inconsistente.",
                            true,
                            DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                            null));
            return new DevicePedidoIdempotencyRecordState(rec, toResponse(pedido, List.of(), List.of(), true));
        }
        if (rec.getStatus() == DevicePedidoIdempotencyStatus.IN_PROGRESS) {
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_IDEMPOTENCY_CONFLICT,
                    "Pedido em processamento para esta idempotencyKey/clientRequestId.",
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                    null);
        }
        if (rec.getStatus() == DevicePedidoIdempotencyStatus.FAILED) {
            rec.setStatus(DevicePedidoIdempotencyStatus.IN_PROGRESS);
            rec.setErrorCode(null);
            idempotencyRepository.saveAndFlush(rec);
            return new DevicePedidoIdempotencyRecordState(rec, null);
        }
        throw new DeviceApiException(HttpStatus.CONFLICT,
                DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_IDEMPOTENCY_CONFLICT,
                "Pedido falhou anteriormente para esta idempotencyKey/clientRequestId.",
                true,
                DeviceErrorResponse.DeviceRecoveryAction.RETRY,
                null);
    }

    private void completeIdempotency(DevicePedidoIdempotencyRecord rec, Pedido pedido) {
        rec.setPedido(pedido);
        rec.setStatus(DevicePedidoIdempotencyStatus.COMPLETED);
        rec.setErrorCode(null);
        idempotencyRepository.save(rec);
    }

    private void failIdempotency(DevicePedidoIdempotencyRecord rec, String errorCode) {
        if (rec == null) return;
        rec.setStatus(DevicePedidoIdempotencyStatus.FAILED);
        rec.setErrorCode(errorCode);
        idempotencyRepository.save(rec);
    }

    private String computeRequestHash(DeviceCriarPedidoRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("clientRequestId=").append(nullSafe(req.getClientRequestId())).append("|");
        sb.append("mesaId=").append(req.getMesaId()).append("|");
        sb.append("qrCodeId=").append(req.getQrCodeId()).append("|");
        sb.append("obs=").append(nullSafe(trimObs(req.getObservacao()))).append("|");

        List<DeviceCriarPedidoItemRequest> itens = new ArrayList<>(req.getItens());
        itens.sort(Comparator.comparing(DeviceCriarPedidoItemRequest::getProdutoId));
        for (DeviceCriarPedidoItemRequest it : itens) {
            sb.append("p=").append(it.getProdutoId())
                    .append(",q=").append(it.getQuantidade())
                    .append(",o=").append(nullSafe(trimObs(it.getObservacao())))
                    .append(";");
        }
        return sha256Hex(sb.toString());
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return sha256HexFallback(value);
        }
    }

    private String sha256HexFallback(String value) {
        // fallback mínimo (não criptográfico forte, mas evita null)
        return Integer.toHexString(value != null ? value.hashCode() : 0);
    }

    private Map<Cozinha, List<DeviceCriarPedidoItemRequest>> agruparItensPorCozinha(Long unidadeAtendimentoId,
                                                                                    List<DeviceCriarPedidoItemRequest> itens,
                                                                                    Map<Long, Produto> produtos) {
        Map<Cozinha, List<DeviceCriarPedidoItemRequest>> out = new java.util.HashMap<>();
        for (DeviceCriarPedidoItemRequest item : itens) {
            Produto produto = produtos.get(item.getProdutoId());
            if (produto == null) {
                throw new DeviceApiException(HttpStatus.NOT_FOUND,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_PRODUCT_NOT_FOUND,
                        "Produto inválido ou inexistente.",
                        true,
                        DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                        null);
            }
            Cozinha cozinha = subPedidoService.determinarCozinha(produto, unidadeAtendimentoId);
            if (!Boolean.TRUE.equals(cozinha.getAtiva())) {
                throw new DeviceApiException(HttpStatus.CONFLICT,
                        DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_PRODUCT_UNAVAILABLE,
                        "Produto inválido ou indisponível.",
                        true,
                        DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                        null);
            }
            out.computeIfAbsent(cozinha, k -> new ArrayList<>()).add(item);
        }
        return out;
    }

    private SessaoConsumo resolverOuCriarSessaoMinima(Tenant tenant, Instituicao instituicao, UnidadeAtendimento unidadeAtendimento, Mesa mesa) {
        Long tenantId = tenant != null ? tenant.getId() : null;
        if (tenantId == null) throw new DeviceApiException(HttpStatus.CONFLICT, DeviceErrorResponse.DeviceErrorCode.DEVICE_INTERNAL_ERROR,
                "Tenant inválido.", false, DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT, null);
        try {
            return sessaoConsumoService.resolveOrCreateSessaoAnonima(
                    tenantId,
                    instituicao,
                    unidadeAtendimento,
                    mesa,
                    TipoSessao.POS_PAGO,
                    false
            );
        } catch (BusinessException ex) {
            throw new DeviceApiException(HttpStatus.CONFLICT,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_INTERNAL_ERROR,
                    ex.getMessage(),
                    true,
                    DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                    null);
        }
    }

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof DevicePrincipal dp)) {
            throw new DeviceUnauthorizedException("Dispositivo não autenticado.");
        }
        return (DevicePrincipal) auth.getPrincipal();
    }

    private void requireCapability(DevicePrincipal device, DeviceCapability capability) {
        if (device == null || device.capabilities() == null || !device.capabilities().contains(capability)) {
            throw new DeviceApiException(HttpStatus.FORBIDDEN,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_ORDER_CREATE_FORBIDDEN,
                    "Dispositivo sem permissão para criar pedido.",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.CONTACT_SUPPORT,
                    null);
        }
    }

    private String trimObs(String obs) {
        if (obs == null) return null;
        String s = obs.trim();
        if (s.isEmpty()) return null;
        if (s.length() > deviceOrdersProperties.getMaxObservacaoLength()) {
            return s.substring(0, deviceOrdersProperties.getMaxObservacaoLength());
        }
        return s;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
