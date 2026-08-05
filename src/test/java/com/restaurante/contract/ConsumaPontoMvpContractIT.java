package com.restaurante.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumaPontoMvpContractIT {

    private static final Path CONTRACT = Path.of("docs/contracts/mvp/CONSUMA_PONTO_MVP_V1.contract.json");
    private static final Path OPENAPI = Path.of("docs/contracts/mvp/CONSUMA_PONTO_MVP_V1.openapi.json");
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void manifestFreezesReleaseAuthAndForbiddenTenantHeaders() throws IOException {
        JsonNode root = json.readTree(CONTRACT.toFile());

        assertThat(root.path("contractVersion").asText()).isEqualTo("1.0.0");
        assertThat(root.path("backendBaseCommit").asText())
                .isEqualTo("813503ea0513a5e2761ecb7a092b0a9ff5cb5cb0");
        assertThat(root.path("releaseVertical").asText()).isEqualTo("CONSUMA_PONTO");
        assertThat(root.path("restReleaseExposure").asText()).isEqualTo("PRESERVED_NOT_RELEASED");
        assertThat(root.path("headers").path("X-Tenant-Id").path("classification").asText()).isEqualTo("FORBIDDEN");
        assertThat(root.path("headers").path("X-Tenant-Code").path("classification").asText()).isEqualTo("FORBIDDEN");
        assertThat(root.path("headers").path("X-Business-Id").path("classification").asText()).isEqualTo("FORBIDDEN");

        assertEndpoint(root, "GET", "/auth/tenants", "GLOBAL");
        assertEndpoint(root, "POST", "/auth/tenant/select", "GLOBAL");
        assertEndpoint(root, "GET", "/tenant/me", "TENANT");
        assertEndpoint(root, "GET", "/public/q/{token}/cardapio", "NONE");
    }

    @Test
    void openApiIsValidJsonWithResolvedReferencesAndUniqueOperationIds() throws IOException {
        JsonNode api = json.readTree(OPENAPI.toFile());
        assertThat(api.path("openapi").asText()).startsWith("3.");
        assertThat(api.path("paths").size()).isGreaterThanOrEqualTo(30);
        assertThat(api.path("components").path("securitySchemes").has("globalBearer")).isTrue();
        assertThat(api.path("components").path("securitySchemes").has("tenantBearer")).isTrue();

        Set<String> operationIds = new HashSet<>();
        walk(api.path("paths"), node -> {
            if (node.isObject() && node.has("operationId")) {
                assertThat(operationIds.add(node.path("operationId").asText()))
                        .as("operationId must be unique: %s", node.path("operationId").asText())
                        .isTrue();
                assertThat(node.path("responses").size()).isPositive();
            }
            if (node.isTextual() && node.asText().startsWith("#/")) {
                assertThat(resolve(api, node.asText()))
                        .as("OpenAPI reference must resolve: %s", node.asText())
                        .isNotNull();
            }
        });
    }

    @Test
    void paymentOrderInvariantAndAllowedActionsMatchBackendSources() throws IOException {
        JsonNode root = json.readTree(CONTRACT.toFile());
        assertThat(root.path("idempotency").path("paymentOrderInvariant").asText())
                .contains("MUST NOT exist before a valid order acceptance");
        assertThat(strings(root.path("allowedActions"))).containsExactlyInAnyOrder(
                "ACCEPT_ORDER", "REJECT_ORDER", "CANCEL_ORDER", "CONFIRM_PAYMENT", "VIEW_PAYMENT",
                "VIEW_EXTRACT", "START_PREPARATION", "MARK_READY", "MARK_DELIVERED");

        String transitions = Files.readString(Path.of(
                "src/main/java/com/restaurante/service/operacional/PedidoStatusTransitionService.java"));
        String affordances = Files.readString(Path.of(
                "src/main/java/com/restaurante/service/operacional/PedidoAllowedActionsService.java"));
        String existingInvariantTest = Files.readString(Path.of(
                "src/test/java/com/restaurante/qr/PublicQrPagamentoStartIT.java"));

        assertThat(transitions).contains("garantirOrdemPagamentoPedidoAposAceite");
        assertThat(transitions.indexOf("garantirOrdemPagamentoPedidoAposAceite"))
                .isGreaterThan(transitions.indexOf("aceitarPedido"));
        assertThat(affordances).contains("CONFIRM_PAYMENT", "MARK_DELIVERED", "requiresAcceptance");
        assertThat(existingInvariantTest).contains("Pagamento disponível apenas após aceite do pedido");
        assertThat(existingInvariantTest).contains("idempot");
    }

    @Test
    void stateEnumsAreExactAndNoPostBasePdvEndpointIsFrozen() throws IOException {
        JsonNode root = json.readTree(CONTRACT.toFile());
        assertThat(strings(root.path("states").path("pedido")))
                .containsExactly("CRIADO", "EM_ANDAMENTO", "FINALIZADO", "CANCELADO");
        assertThat(strings(root.path("states").path("turno")))
                .containsExactly("ABERTO", "EM_FECHO", "FECHADO", "CANCELADO");
        assertThat(root.path("contractGaps").toString()).contains("CG-04");
        assertThat(root.path("endpoints").toString()).doesNotContain("/tenant/pdv/pedidos");
    }

    private void assertEndpoint(JsonNode root, String method, String path, String scope) {
        boolean found = false;
        for (JsonNode endpoint : root.path("endpoints")) {
            if (method.equals(endpoint.path("method").asText())
                    && path.equals(endpoint.path("path").asText())
                    && scope.equals(endpoint.path("authScope").asText())) {
                found = true;
            }
        }
        assertThat(found).as("%s %s must use %s", method, path, scope).isTrue();
    }

    private static java.util.List<String> strings(JsonNode array) {
        java.util.List<String> values = new java.util.ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static JsonNode resolve(JsonNode root, String reference) {
        JsonNode current = root;
        for (String raw : reference.substring(2).split("/")) {
            current = current.path(raw.replace("~1", "/").replace("~0", "~"));
        }
        return current.isMissingNode() ? null : current;
    }

    private static void walk(JsonNode node, java.util.function.Consumer<JsonNode> consumer) {
        consumer.accept(node);
        if (node.isContainerNode()) {
            Iterator<JsonNode> children = node.elements();
            while (children.hasNext()) {
                walk(children.next(), consumer);
            }
        }
    }
}
