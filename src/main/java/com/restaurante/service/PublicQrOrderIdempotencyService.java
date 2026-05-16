package com.restaurante.service;

import com.restaurante.dto.request.PublicQrPedidoItemRequest;
import com.restaurante.dto.request.PublicQrPedidoRequest;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.PublicQrOrderRequest;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.PublicQrOrderRequestStatus;
import com.restaurante.repository.PublicQrOrderRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PublicQrOrderIdempotencyService {

    private static final int IDEMPOTENCY_KEY_MIN = 8;
    private static final int IDEMPOTENCY_KEY_MAX = 100;

    private final PublicQrOrderRequestRepository repository;

    public String requireKey(String headerKey, String bodyKey) {
        String key = (headerKey != null && !headerKey.isBlank()) ? headerKey : bodyKey;
        if (key == null || key.isBlank()) {
            throw new BusinessException("Idempotency-Key é obrigatório para criar pedido por QR.");
        }
        String trimmed = key.trim();
        if (trimmed.length() < IDEMPOTENCY_KEY_MIN || trimmed.length() > IDEMPOTENCY_KEY_MAX) {
            throw new BusinessException("Idempotency-Key inválida.");
        }
        return trimmed;
    }

    public String computeRequestHash(PublicQrPedidoRequest request) {
        String canonical = canonicalize(request);
        return sha256Hex(canonical);
    }

    /**
     * Inicia idempotência retornando:
     * - COMPLETED: retorna pedido existente
     * - PROCESSING: conflito (409)
     * - inexistente: cria PROCESSING e retorna o registro criado
     */
    @Transactional
    public StartResult startOrGet(Tenant tenant, QrCodeOperacional qr, String key, String requestHash) {
        Optional<PublicQrOrderRequest> existingOpt = repository
                .findByTenantIdAndQrCodeOperacionalIdAndIdempotencyKey(tenant.getId(), qr.getId(), key);

        if (existingOpt.isPresent()) {
            PublicQrOrderRequest existing = existingOpt.get();
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new ConflictException("Idempotency-Key reutilizada com payload diferente.");
            }
            if (existing.getStatus() == PublicQrOrderRequestStatus.PROCESSING) {
                throw new ConflictException("Pedido ainda em processamento. Tente consultar novamente.");
            }
            if (existing.getStatus() == PublicQrOrderRequestStatus.COMPLETED && existing.getPedido() != null) {
                return StartResult.completed(existing, existing.getPedido());
            }
            // FAILED or COMPLETED without pedido: allow retry by reusing the same row as PROCESSING
            existing.setStatus(PublicQrOrderRequestStatus.PROCESSING);
            repository.save(existing);
            return StartResult.processing(existing);
        }

        PublicQrOrderRequest created = new PublicQrOrderRequest();
        created.setTenant(tenant);
        created.setQrCodeOperacional(qr);
        created.setIdempotencyKey(key);
        created.setRequestHash(requestHash);
        created.setStatus(PublicQrOrderRequestStatus.PROCESSING);

        try {
            PublicQrOrderRequest saved = repository.saveAndFlush(created);
            return StartResult.processing(saved);
        } catch (DataIntegrityViolationException e) {
            // Race: another request inserted same unique key; re-read and apply the rules
            PublicQrOrderRequest raced = repository
                    .findByTenantIdAndQrCodeOperacionalIdAndIdempotencyKey(tenant.getId(), qr.getId(), key)
                    .orElseThrow(() -> e);
            if (!requestHash.equals(raced.getRequestHash())) {
                throw new ConflictException("Idempotency-Key reutilizada com payload diferente.");
            }
            if (raced.getStatus() == PublicQrOrderRequestStatus.PROCESSING) {
                throw new ConflictException("Pedido ainda em processamento. Tente consultar novamente.");
            }
            if (raced.getStatus() == PublicQrOrderRequestStatus.COMPLETED && raced.getPedido() != null) {
                return StartResult.completed(raced, raced.getPedido());
            }
            raced.setStatus(PublicQrOrderRequestStatus.PROCESSING);
            repository.save(raced);
            return StartResult.processing(raced);
        }
    }

    @Transactional
    public void markCompleted(PublicQrOrderRequest req, Pedido pedido) {
        req.setPedido(pedido);
        req.setStatus(PublicQrOrderRequestStatus.COMPLETED);
        repository.save(req);
    }

    @Transactional
    public void markFailed(PublicQrOrderRequest req) {
        req.setStatus(PublicQrOrderRequestStatus.FAILED);
        repository.save(req);
    }

    private static String canonicalize(PublicQrPedidoRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("clienteNome=").append(norm(request.getClienteNome())).append('\n');
        sb.append("clienteTelefone=").append(norm(request.getClienteTelefone())).append('\n');
        sb.append("observacao=").append(norm(request.getObservacao())).append('\n');

        List<PublicQrPedidoItemRequest> itens = request.getItens() != null ? request.getItens() : List.of();
        itens.stream()
                .sorted(Comparator
                        .comparing(PublicQrPedidoItemRequest::getProdutoId, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(i -> norm(i.getObservacao())))
                .forEach(i -> {
                    sb.append("item:produtoId=").append(i.getProdutoId())
                            .append("|qtd=").append(i.getQuantidade())
                            .append("|obs=").append(norm(i.getObservacao()))
                            .append('\n');
                });
        return sb.toString();
    }

    private static String norm(String s) {
        if (s == null) return "";
        return s.trim().replace("\r\n", "\n").replace("\r", "\n");
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular hash de idempotência.", e);
        }
    }

    public record StartResult(PublicQrOrderRequest request, Pedido completedPedido) {
        static StartResult completed(PublicQrOrderRequest req, Pedido pedido) { return new StartResult(req, pedido); }
        static StartResult processing(PublicQrOrderRequest req) { return new StartResult(req, null); }
        boolean isCompleted() { return completedPedido != null; }
    }
}
