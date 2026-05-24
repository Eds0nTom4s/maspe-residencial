package com.restaurante.consumo.participante.web;

import com.restaurante.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Prompt 41.5 — Extrai o ownerActionToken da request de forma segura.
 * <p>
 * Precedência:
 * 1. Header {@code X-Owner-Action-Token}
 * 2. Campo {@code ownerActionToken} no body (passado como parâmetro explícito)
 * <p>
 * REGRAS DE SEGURANÇA:
 * - Nunca aceitar token em query param.
 * - Nunca logar o valor do token.
 * - Retornar erro claro se nulo/blank.
 */
@Component
public class OwnerActionTokenExtractor {

    public static final String HEADER_NAME = "X-Owner-Action-Token";

    /**
     * Extrai o token dando prioridade ao header.
     * Se o header estiver ausente, usa o valor do body.
     * Lança {@link BusinessException} se nenhum dos dois for fornecido/válido.
     *
     * @param request       HttpServletRequest para ler o header
     * @param bodyToken     valor extraído do body pelo controller (pode ser null)
     * @param required      se true, lança exceção se não encontrado; se false, retorna null
     * @return raw token (nunca logar)
     */
    public String extract(HttpServletRequest request, String bodyToken, boolean required) {
        String headerToken = request.getHeader(HEADER_NAME);
        String raw = (headerToken != null && !headerToken.isBlank()) ? headerToken : bodyToken;
        if ((raw == null || raw.isBlank()) && required) {
            throw new BusinessException("OWNER_ACTION_TOKEN_REQUIRED");
        }
        return (raw != null && !raw.isBlank()) ? raw : null;
    }

    /**
     * Versão conveniente — sempre obrigatório.
     */
    public String extractRequired(HttpServletRequest request, String bodyToken) {
        return extract(request, bodyToken, true);
    }

    /**
     * Garante que o token NÃO veio por query param.
     * Controllers que queiram fazer esta verificação devem chamar antes de processar.
     */
    public void assertNotInQueryParam(HttpServletRequest request) {
        if (request.getParameter("ownerActionToken") != null
                || request.getParameter("owner_action_token") != null) {
            throw new BusinessException("OWNER_ACTION_TOKEN_QUERY_PARAM_NOT_ALLOWED");
        }
    }
}
