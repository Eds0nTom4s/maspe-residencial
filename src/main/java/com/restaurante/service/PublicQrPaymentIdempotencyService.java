package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.PublicQrPaymentRequest;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.PublicQrPaymentRequestStatus;
import com.restaurante.repository.PublicQrPaymentRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PublicQrPaymentIdempotencyService {

    private static final int IDEMPOTENCY_KEY_MIN = 8;
    private static final int IDEMPOTENCY_KEY_MAX = 100;

    private final PublicQrPaymentRequestRepository repository;

    public String requireKey(String headerKey, String bodyKey) {
        String key = (headerKey != null && !headerKey.isBlank()) ? headerKey : bodyKey;
        if (key == null || key.isBlank()) {
            throw new BusinessException("Idempotency-Key é obrigatório para iniciar pagamento por QR.");
        }
        String trimmed = key.trim();
        if (trimmed.length() < IDEMPOTENCY_KEY_MIN || trimmed.length() > IDEMPOTENCY_KEY_MAX) {
            throw new BusinessException("Idempotency-Key inválida.");
        }
        return trimmed;
    }

    public String computeRequestHash(String metodo, String telefone) {
        String canonical = "metodo=" + norm(metodo) + "\ntelefone=" + norm(telefone) + "\n";
        return sha256Hex(canonical);
    }

    @Transactional
    public StartResult startOrGet(Tenant tenant, Pedido pedido, String key, String requestHash) {
        Optional<PublicQrPaymentRequest> existingOpt = repository.findByTenantIdAndPedidoIdAndIdempotencyKey(
                tenant.getId(), pedido.getId(), key);

        if (existingOpt.isPresent()) {
            PublicQrPaymentRequest existing = existingOpt.get();
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new ConflictException("Idempotency-Key reutilizada com payload diferente.");
            }
            if (existing.getStatus() == PublicQrPaymentRequestStatus.PROCESSING) {
                throw new ConflictException("Pagamento ainda em processamento. Tente novamente.");
            }
            if (existing.getStatus() == PublicQrPaymentRequestStatus.COMPLETED && existing.getPagamento() != null) {
                return StartResult.completed(existing, existing.getPagamento());
            }
            existing.setStatus(PublicQrPaymentRequestStatus.PROCESSING);
            repository.save(existing);
            return StartResult.processing(existing);
        }

        PublicQrPaymentRequest created = new PublicQrPaymentRequest();
        created.setTenant(tenant);
        created.setPedido(pedido);
        created.setIdempotencyKey(key);
        created.setRequestHash(requestHash);
        created.setStatus(PublicQrPaymentRequestStatus.PROCESSING);

        try {
            PublicQrPaymentRequest saved = repository.saveAndFlush(created);
            return StartResult.processing(saved);
        } catch (DataIntegrityViolationException e) {
            PublicQrPaymentRequest raced = repository.findByTenantIdAndPedidoIdAndIdempotencyKey(
                    tenant.getId(), pedido.getId(), key).orElseThrow(() -> e);
            if (!requestHash.equals(raced.getRequestHash())) {
                throw new ConflictException("Idempotency-Key reutilizada com payload diferente.");
            }
            if (raced.getStatus() == PublicQrPaymentRequestStatus.PROCESSING) {
                throw new ConflictException("Pagamento ainda em processamento. Tente novamente.");
            }
            if (raced.getStatus() == PublicQrPaymentRequestStatus.COMPLETED && raced.getPagamento() != null) {
                return StartResult.completed(raced, raced.getPagamento());
            }
            raced.setStatus(PublicQrPaymentRequestStatus.PROCESSING);
            repository.save(raced);
            return StartResult.processing(raced);
        }
    }

    @Transactional
    public void markCompleted(PublicQrPaymentRequest req, Pagamento pagamento) {
        req.setPagamento(pagamento);
        req.setStatus(PublicQrPaymentRequestStatus.COMPLETED);
        repository.save(req);
    }

    @Transactional
    public void markFailed(PublicQrPaymentRequest req) {
        req.setStatus(PublicQrPaymentRequestStatus.FAILED);
        repository.save(req);
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

    public record StartResult(PublicQrPaymentRequest request, Pagamento completedPayment) {
        static StartResult completed(PublicQrPaymentRequest req, Pagamento pagamento) { return new StartResult(req, pagamento); }
        static StartResult processing(PublicQrPaymentRequest req) { return new StartResult(req, null); }
        boolean isCompleted() { return completedPayment != null; }
    }
}

