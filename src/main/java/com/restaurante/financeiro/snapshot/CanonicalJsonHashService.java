package com.restaurante.financeiro.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Gera hash criptográfico (ex.: SHA-256) sobre um JSON canônico determinístico.
 *
 * Regras MVP:
 * - Ordena chaves de objetos de forma determinística (ordem lexicográfica).
 * - Mantém ordem de arrays (a ordem deve ser estável na origem).
 * - Remove campos excluídos do escopo (ex.: "integridade") para evitar hash circular.
 */
@Service
@RequiredArgsConstructor
public class CanonicalJsonHashService {

    private final ObjectMapper objectMapper;

    public String sha256HexCanonical(JsonNode node, List<String> excludedTopLevelKeys) {
        return hashHexCanonical("SHA-256", node, excludedTopLevelKeys);
    }

    public String hashHexCanonical(String algorithm, JsonNode node, List<String> excludedTopLevelKeys) {
        try {
            JsonNode sanitized = deepCopyAndRemoveTopLevelKeys(node, excludedTopLevelKeys);
            JsonNode canonical = canonicalize(sanitized);
            byte[] bytes = canonicalBytes(canonical);
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(bytes);
            return toHexLower(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular hash canônico do snapshot.", e);
        }
    }

    private JsonNode deepCopyAndRemoveTopLevelKeys(JsonNode node, List<String> excludedTopLevelKeys) {
        if (node == null) return null;
        try {
            JsonNode copy = objectMapper.readTree(objectMapper.writeValueAsBytes(node));
            if (copy instanceof ObjectNode on && excludedTopLevelKeys != null) {
                for (String k : excludedTopLevelKeys) {
                    on.remove(k);
                }
            }
            return copy;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao preparar JSON para canonicalização/hash.", e);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null) return null;
        if (node.isObject()) {
            ObjectNode out = objectMapper.createObjectNode();
            ObjectNode obj = (ObjectNode) node;
            List<String> names = new ArrayList<>();
            obj.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            for (String name : names) {
                out.set(name, canonicalize(obj.get(name)));
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = objectMapper.createArrayNode();
            ArrayNode arr = (ArrayNode) node;
            for (JsonNode el : arr) {
                out.add(canonicalize(el));
            }
            return out;
        }
        return node;
    }

    private byte[] canonicalBytes(JsonNode canonical) throws JsonProcessingException {
        // Ainda ordenamos map entries por segurança (caso algum Map escape como JsonNode via POJO).
        ObjectMapper canonicalMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        String json = canonicalMapper.writeValueAsString(canonical);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String toHexLower(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
