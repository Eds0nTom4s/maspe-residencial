package com.restaurante.model.entity;

import com.restaurante.model.enums.StatusSessaoConsumo;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade SessaoConsumo — evento TEMPORAL de ocupação de uma mesa.
 *
 * <p>Criada pelo atendente ao sentar clientes. Encerrada ao fechar a conta.
 * Nunca é reutilizada: cada ocupação gera uma nova instância auditável.
 *
 * <p>Ciclo de vida:
 * <pre>
 *   ABERTA → AGUARDANDO_PAGAMENTO → ENCERRADA
 *   ABERTA →                      → ENCERRADA  (fechamento direto)
 * </pre>
 *
 * <p>Regras de negócio:
 * <ul>
 *   <li>Uma mesa só pode ter UMA SessaoConsumo com status ABERTA por vez.</li>
 *   <li>Pedidos são vinculados à SessaoConsumo, não diretamente à mesa.</li>
 *   <li>Encerrar a sessão NÃO altera a mesa — ela fica automaticamente DISPONÍVEL.</li>
 *   <li>O modo anônimo usa {@code qrCodePortador} como identificador de fundo.</li>
 * </ul>
 */
@Entity
@Table(name = "sessoes_consumo", indexes = {
    @Index(name = "idx_sessao_mesa", columnList = "mesa_id"),
    @Index(name = "idx_sessao_status", columnList = "status"),
    @Index(name = "idx_sessao_cliente", columnList = "cliente_id"),
    @Index(name = "idx_sessao_aberta_em", columnList = "aberta_em")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessaoConsumo extends BaseEntity {

    /**
     * Mesa física associada (proprietário da sessão).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesa_id", nullable = false)
    private Mesa mesa;

    /**
     * Cliente identificado (opcional — nulo no fluxo anônimo).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    /**
     * Atendente que abriu a sessão (opcional).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendente_id")
    private Atendente aberturaPor;

    /**
     * Timestamp de abertura da sessão.
     */
    @Column(name = "aberta_em", nullable = false)
    @Builder.Default
    private LocalDateTime abertaEm = LocalDateTime.now();

    /**
     * Timestamp de encerramento da sessão (null enquanto ABERTA).
     */
    @Column(name = "fechada_em")
    private LocalDateTime fechadaEm;

    /**
     * Status atual da sessão.
     * NUNCA representa o status da mesa — o status da mesa é DERIVADO.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private StatusSessaoConsumo status = StatusSessaoConsumo.ABERTA;

    /**
     * Flag de modo anônimo (sem identidade do cliente).
     *
     * <p>Quando {@code true}:
     * <ul>
     *   <li>{@code cliente} é null.</li>
     *   <li>{@code qrCodePortador} é o único identificador do fundo de consumo.</li>
     *   <li>Pós-pago não é permitido.</li>
     * </ul>
     */
    @Column(name = "modo_anonimo", nullable = false)
    @Builder.Default
    private Boolean modoAnonimo = false;

    /**
     * Token do portador anônimo (QR Code da mesa usado como chave do fundo).
     * Apenas preenchido quando {@code modoAnonimo = true}.
     */
    @Column(name = "qr_code_portador", length = 100)
    private String qrCodePortador;

    /**
     * Pedidos realizados dentro desta sessão.
     */
    @OneToMany(mappedBy = "sessaoConsumo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Pedido> pedidos = new ArrayList<>();

    // ──────────────────────────────────────────────────────────────────────────
    // Comportamentos de domínio
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Verifica se a sessão está ativa (pode receber pedidos).
     */
    public boolean isAberta() {
        return status == StatusSessaoConsumo.ABERTA;
    }

    /**
     * Encerra a sessão — a mesa fica DISPONÍVEL automaticamente (status derivado).
     */
    public void encerrar() {
        this.status = StatusSessaoConsumo.ENCERRADA;
        this.fechadaEm = LocalDateTime.now();
    }

    /**
     * Sinaliza que a sessão está aguardando pagamento.
     * Mantém a mesa como OCUPADA até o encerramento definitivo.
     */
    public void aguardarPagamento() {
        this.status = StatusSessaoConsumo.AGUARDANDO_PAGAMENTO;
    }

    /**
     * Calcula o total de consumo somando todos os pedidos da sessão.
     */
    public BigDecimal calcularTotal() {
        return pedidos.stream()
                .map(p -> p.getTotal() != null ? p.getTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Verifica se a sessão pode receber novos pedidos.
     */
    public boolean podeReceberPedidos() {
        return status == StatusSessaoConsumo.ABERTA;
    }
}
