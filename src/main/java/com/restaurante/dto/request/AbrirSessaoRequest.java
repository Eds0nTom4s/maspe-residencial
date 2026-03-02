package com.restaurante.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para abertura de uma nova SessaoConsumo.
 *
 * <p>Operação executada pelo ATENDENTE ao sentar clientes em uma mesa.
 * Cada chamada gera uma nova sessão — nunca reutiliza a anterior.
 *
 * <p>Fluxos suportados:
 * <ul>
 *   <li><b>Identificado</b>: {@code modoAnonimo = false} — exige {@code telefoneCliente}.</li>
 *   <li><b>Anônimo</b>: {@code modoAnonimo = true} — nenhum cliente necessário;
 *       o QR Code da mesa atua como token do fundo de consumo.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbrirSessaoRequest {

    @NotNull(message = "ID da mesa é obrigatório")
    private Long mesaId;

    /**
     * Telefone do cliente para o fluxo identificado.
     * Nulo/vazio quando {@code modoAnonimo = true}.
     */
    private String telefoneCliente;

    /**
     * Ativa o modo de consumo anônimo (sem identidade do cliente).
     * Quando {@code true}: pós-pago bloqueado; QR Code é o único identificador.
     */
    @Builder.Default
    private boolean modoAnonimo = false;

    /**
     * ID do atendente que abriu a sessão (opcional — auditoria).
     */
    private Long atendenteId;
}
