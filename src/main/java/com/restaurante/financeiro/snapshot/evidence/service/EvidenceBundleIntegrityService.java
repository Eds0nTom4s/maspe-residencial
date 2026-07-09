package com.restaurante.financeiro.snapshot.evidence.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurante.financeiro.snapshot.CanonicalJsonHashService;
import com.restaurante.financeiro.snapshot.SnapshotSignatureService;
import com.restaurante.financeiro.snapshot.SnapshotSignatureFailureReason;
import com.restaurante.financeiro.snapshot.dto.SnapshotSignatureResult;
import com.restaurante.financeiro.snapshot.dto.SnapshotSignatureVerificationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EvidenceBundleIntegrityService {

    private final ObjectMapper objectMapper;
    private final CanonicalJsonHashService canonicalJsonHashService;
    private final SnapshotSignatureService signatureService;

    public EvidenceBundleIntegrity calcularIntegridadeBundle(String hashAlgorithm,
                                                            String canonicalizationVersion,
                                                            JsonNode bundleNode) {
        String bundleHash = canonicalJsonHashService.hashHexCanonical(hashAlgorithm, bundleNode, List.of());
        SnapshotSignatureResult sig = signatureService.sign(bundleHash);

        EvidenceBundleIntegrity out = new EvidenceBundleIntegrity();
        out.hashAlgorithm = hashAlgorithm;
        out.canonicalizationVersion = canonicalizationVersion;
        out.bundleHash = bundleHash;
        out.signatureAlgorithm = sig.getAlgorithm();
        out.signatureKeyId = sig.getKeyId();
        out.bundleSignature = sig.getSignature();
        out.signatureGeneratedAt = sig.getGeneratedAt() != null ? sig.getGeneratedAt() : LocalDateTime.now();
        return out;
    }

    public EvidenceBundleChain calcularChain(String hashAlgorithm,
                                             String canonicalizationVersion,
                                             Long tenantId,
                                             Long turnoId,
                                             int sequenceNumber,
                                             String previousBundleHash,
                                             String bundleHash) {
        String chainHash = calcularChainHash(
                hashAlgorithm,
                canonicalizationVersion,
                tenantId,
                turnoId,
                sequenceNumber,
                previousBundleHash,
                bundleHash
        );
        SnapshotSignatureResult sig = signatureService.sign(chainHash);

        EvidenceBundleChain out = new EvidenceBundleChain();
        out.chainHash = chainHash;
        out.chainSignature = sig.getSignature();
        out.chainSignatureAlgorithm = sig.getAlgorithm();
        out.chainSignatureKeyId = sig.getKeyId();
        out.chainSignatureGeneratedAt = sig.getGeneratedAt() != null ? sig.getGeneratedAt() : LocalDateTime.now();
        return out;
    }

    public String calcularChainHash(String hashAlgorithm,
                                   String canonicalizationVersion,
                                   Long tenantId,
                                   Long turnoId,
                                   int sequenceNumber,
                                   String previousBundleHash,
                                   String bundleHash) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("tenantId", tenantId);
        payload.put("turnoId", turnoId);
        payload.put("sequenceNumber", sequenceNumber);
        if (previousBundleHash != null) payload.put("previousBundleHash", previousBundleHash);
        payload.put("bundleHash", bundleHash);
        payload.put("canonicalizationVersion", canonicalizationVersion);
        return canonicalJsonHashService.hashHexCanonical(hashAlgorithm, payload, List.of());
    }

    public EvidenceBundleVerification verificar(String hashAlgorithm,
                                                JsonNode bundleNode,
                                                String bundleHashPersistido,
                                                String bundleSignaturePersistida,
                                                String signatureKeyId,
                                                String chainHashPersistido,
                                                String chainSignaturePersistida,
                                                String chainSignatureKeyId,
                                                String expectedChainHash,
                                                String expectedPreviousBundleHash,
                                                String previousBundleHashPersistido) {
        EvidenceBundleVerification v = new EvidenceBundleVerification();
        v.verificadoEm = LocalDateTime.now();

        String recalculado = canonicalJsonHashService.hashHexCanonical(hashAlgorithm, bundleNode, List.of());
        v.bundleHashPersistido = bundleHashPersistido;
        v.bundleHashRecalculado = recalculado;
        v.bundleHashValido = bundleHashPersistido != null && bundleHashPersistido.equals(recalculado);

        SnapshotSignatureVerificationResult sigRes = signatureService.verify(
                bundleHashPersistido,
                bundleSignaturePersistida,
                signatureKeyId
        );
        v.bundleSignatureValida = sigRes.isSignatureValid();
        v.bundleSignatureKeyFound = sigRes.isKeyFound();
        v.bundleSignatureKeyStatus = sigRes.getKeyStatus() != null ? sigRes.getKeyStatus().name() : null;
        v.bundleSignatureFailureReason = sigRes.getFailureReason() != null ? sigRes.getFailureReason().name() : null;

        // chain
        v.chainHashPersistido = chainHashPersistido;
        v.chainHashValido = chainHashPersistido != null && chainHashPersistido.equals(expectedChainHash);
        SnapshotSignatureVerificationResult chainSigRes = signatureService.verify(
                chainHashPersistido,
                chainSignaturePersistida,
                chainSignatureKeyId
        );
        v.chainSignatureValida = chainSigRes.isSignatureValid();
        v.chainSignatureKeyFound = chainSigRes.isKeyFound();
        v.chainSignatureKeyStatus = chainSigRes.getKeyStatus() != null ? chainSigRes.getKeyStatus().name() : null;
        v.chainSignatureFailureReason = chainSigRes.getFailureReason() != null ? chainSigRes.getFailureReason().name() : null;

        // previous link
        if (expectedPreviousBundleHash == null) {
            v.previousLinkValido = (previousBundleHashPersistido == null);
        } else {
            v.previousLinkValido = expectedPreviousBundleHash.equals(previousBundleHashPersistido);
        }

        v.valido = Boolean.TRUE.equals(v.bundleHashValido)
                && Boolean.TRUE.equals(v.bundleSignatureValida)
                && Boolean.TRUE.equals(v.chainHashValido)
                && Boolean.TRUE.equals(v.chainSignatureValida)
                && Boolean.TRUE.equals(v.previousLinkValido);

        if (!Boolean.TRUE.equals(v.bundleHashValido)) {
            v.failureReason = "BUNDLE_HASH_MISMATCH";
        } else if (!Boolean.TRUE.equals(v.bundleSignatureValida)) {
            v.failureReason = v.bundleSignatureFailureReason != null ? v.bundleSignatureFailureReason : SnapshotSignatureFailureReason.SIGNATURE_MISMATCH.name();
        } else if (!Boolean.TRUE.equals(v.chainHashValido)) {
            v.failureReason = "CHAIN_HASH_MISMATCH";
        } else if (!Boolean.TRUE.equals(v.chainSignatureValida)) {
            v.failureReason = v.chainSignatureFailureReason != null ? v.chainSignatureFailureReason : SnapshotSignatureFailureReason.SIGNATURE_MISMATCH.name();
        } else if (!Boolean.TRUE.equals(v.previousLinkValido)) {
            v.failureReason = "PREVIOUS_LINK_MISMATCH";
        }
        return v;
    }

    public static final class EvidenceBundleIntegrity {
        public String canonicalizationVersion;
        public String hashAlgorithm;
        public String bundleHash;
        public String signatureAlgorithm;
        public String bundleSignature;
        public String signatureKeyId;
        public LocalDateTime signatureGeneratedAt;
    }

    public static final class EvidenceBundleChain {
        public String chainHash;
        public String chainSignatureAlgorithm;
        public String chainSignature;
        public String chainSignatureKeyId;
        public LocalDateTime chainSignatureGeneratedAt;
    }

    public static final class EvidenceBundleVerification {
        public boolean valido;
        public Boolean bundleHashValido;
        public Boolean bundleSignatureValida;
        public Boolean chainHashValido;
        public Boolean chainSignatureValida;
        public Boolean previousLinkValido;

        public String bundleHashPersistido;
        public String bundleHashRecalculado;
        public String chainHashPersistido;
        public LocalDateTime verificadoEm;
        public String failureReason;

        public Boolean bundleSignatureKeyFound;
        public String bundleSignatureKeyStatus;
        public String bundleSignatureFailureReason;

        public Boolean chainSignatureKeyFound;
        public String chainSignatureKeyStatus;
        public String chainSignatureFailureReason;
    }
}
