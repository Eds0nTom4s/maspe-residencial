package com.restaurante.model.enums;

/**
 * Estados de uma SessaoConsumo (evento temporal de ocupação de mesa).
 *
 * ABERTA              → Mesa em uso; pedidos podem ser criados.
 * AGUARDANDO_PAGAMENTO → Consumo encerrado pelo cliente/atendente; aguardando quitação.
 * ENCERRADA           → Pagamento confirmado; sessão auditável, imutável.
 *
 * Ciclo de vida:
 *   ABERTA → AGUARDANDO_PAGAMENTO → ENCERRADA
 *   ABERTA →                      → ENCERRADA  (fechamento direto)
 *
 * ⚠️  O STATUS DA MESA (DISPONÍVEL/OCUPADA) É DERIVADO — nunca persistido.
 *     Mesa OCUPADA  ≡ EXISTS SessaoConsumo WHERE mesa = :mesa AND status = ABERTA
 *     Mesa DISPONÍVEL ≡ NOT EXISTS do mesmo critério
 */
public enum StatusSessaoConsumo {

    ABERTA("Aberta"),
    AGUARDANDO_PAGAMENTO("Aguardando Pagamento"),
    ENCERRADA("Encerrada");

    private final String descricao;

    StatusSessaoConsumo(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
