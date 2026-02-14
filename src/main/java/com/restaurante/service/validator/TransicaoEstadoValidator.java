package com.restaurante.service.validator;

import com.restaurante.exception.PermissaoNegadaException;
import com.restaurante.exception.TransicaoInvalidaException;
import com.restaurante.model.enums.StatusSubPedido;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Validador centralizado de transições de estado
 * 
 * ÚNICA fonte de verdade para regras de transição
 * 
 * Responsabilidades:
 * 1. Validar se transição é válida (estado atual → estado destino)
 * 2. Validar se role tem permissão para executar transição
 * 3. Lançar exceções claras quando inválido
 * 
 * NÃO é responsável por:
 * - Persistir mudanças
 * - Gerar EventLog
 * - Atualizar @Version
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TransicaoEstadoValidator {

    private final Environment environment;

    // Roles com permissão para marcar EM_PREPARACAO
    private static final Set<String> ROLES_COZINHA = Set.of("ROLE_COZINHA", "ROLE_GERENTE", "ROLE_ADMIN");
    
    // Roles com permissão para marcar ENTREGUE
    private static final Set<String> ROLES_ATENDENTE = Set.of("ROLE_ATENDENTE", "ROLE_GERENTE", "ROLE_ADMIN");
    
    // Roles com permissão para CANCELAR
    private static final Set<String> ROLES_GERENCIAL = Set.of("ROLE_GERENTE", "ROLE_ADMIN");

    /**
     * Valida transição completa: estado + permissão
     * 
     * @param estadoAtual Estado atual do SubPedido
     * @param estadoNovo Estado desejado
     * @param roles Roles do usuário autenticado
     * @throws TransicaoInvalidaException se transição inválida
     * @throws PermissaoNegadaException se usuário sem permissão
     */
    public void validarTransicao(StatusSubPedido estadoAtual, StatusSubPedido estadoNovo, Set<String> roles) {
        log.debug("Validando transição: {} → {} (roles: {})", estadoAtual, estadoNovo, roles);
        
        // 1. Validar se transição é válida (independente de permissão)
        validarTransicaoValida(estadoAtual, estadoNovo);
        
        // 2. Validar se usuário tem permissão para executar transição
        validarPermissao(estadoAtual, estadoNovo, roles);
        
        log.debug("Transição válida: {} → {}", estadoAtual, estadoNovo);
    }

    /**
     * Valida se transição entre estados é válida
     * NÃO valida permissões
     */
    private void validarTransicaoValida(StatusSubPedido estadoAtual, StatusSubPedido estadoNovo) {
        // Idempotência: permitir transição para o mesmo estado
        if (estadoAtual == estadoNovo) {
            log.debug("Transição idempotente: {} → {} (permitido)", estadoAtual, estadoNovo);
            return;
        }
        
        // Estados terminais não aceitam transições
        if (estadoAtual.isTerminal()) {
            throw new TransicaoInvalidaException(
                estadoAtual.name(), 
                estadoNovo.name(), 
                "Estado terminal não aceita transições"
            );
        }
        
        // Validar transição usando enum
        if (!estadoAtual.podeTransicionarPara(estadoNovo)) {
            throw new TransicaoInvalidaException(
                estadoAtual.name(), 
                estadoNovo.name()
            );
        }
    }

    /**
     * Valida se usuário tem permissão para executar transição
     */
    private void validarPermissao(StatusSubPedido estadoAtual, StatusSubPedido estadoNovo, Set<String> roles) {
        // Idempotência: sempre permitir (será no-op no service)
        if (estadoAtual == estadoNovo) {
            return;
        }
        
        // Validação específica por transição
        switch (estadoNovo) {
            case PENDENTE -> {
                // CRIADO → PENDENTE: qualquer role autenticada pode (criação automática)
                // Sem validação adicional
            }
            
            case EM_PREPARACAO -> {
                // PENDENTE → EM_PREPARACAO: apenas COZINHA, GERENTE, ADMIN
                if (!contemAlgumaRole(roles, ROLES_COZINHA)) {
                    throw new PermissaoNegadaException(
                        roles.toString(), 
                        "marcar SubPedido como EM_PREPARACAO"
                    );
                }
            }
            
            case PRONTO -> {
                // EM_PREPARACAO → PRONTO: apenas COZINHA, GERENTE, ADMIN
                if (!contemAlgumaRole(roles, ROLES_COZINHA)) {
                    throw new PermissaoNegadaException(
                        roles.toString(), 
                        "marcar SubPedido como PRONTO"
                    );
                }
            }
            
            case ENTREGUE -> {
                // PRONTO → ENTREGUE: apenas ATENDENTE, GERENTE, ADMIN
                if (!contemAlgumaRole(roles, ROLES_ATENDENTE)) {
                    throw new PermissaoNegadaException(
                        roles.toString(), 
                        "marcar SubPedido como ENTREGUE"
                    );
                }
            }
            
            case CANCELADO -> {
                // Qualquer → CANCELADO: apenas GERENTE, ADMIN
                if (!contemAlgumaRole(roles, ROLES_GERENCIAL)) {
                    throw new PermissaoNegadaException(
                        roles.toString(), 
                        "cancelar SubPedido"
                    );
                }
            }
            
            default -> {
                throw new TransicaoInvalidaException(
                    "Transição não mapeada: " + estadoAtual + " → " + estadoNovo
                );
            }
        }
    }

    /**
     * Verifica se conjunto de roles contém alguma role esperada
     * 
     * IMPORTANTE: Em ambiente de teste (profile 'test'), aceita ROLE_ANONYMOUS
     * para permitir testes E2E sem autenticação. Em produção, ROLE_ANONYMOUS
     * sempre é rejeitado.
     */
    private boolean contemAlgumaRole(Set<String> rolesUsuario, Set<String> rolesEsperadas) {
        // Em ambiente de teste, aceitar ROLE_ANONYMOUS
        if (isAmbienteDeTeste() && rolesUsuario.contains("ROLE_ANONYMOUS")) {
            log.debug("⚠️ AMBIENTE DE TESTE: Aceitando ROLE_ANONYMOUS");
            return true;
        }
        
        return rolesUsuario.stream()
            .anyMatch(rolesEsperadas::contains);
    }
    
    /**
     * Verifica se está rodando em ambiente de teste
     */
    private boolean isAmbienteDeTeste() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("test".equals(profile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Valida se motivo de cancelamento foi fornecido
     */
    public void validarMotivoCancelamento(String motivo) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new TransicaoInvalidaException(
                "Cancelamento exige motivo obrigatório"
            );
        }
    }
}
