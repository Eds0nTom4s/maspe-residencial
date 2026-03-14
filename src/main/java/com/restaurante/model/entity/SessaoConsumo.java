package com.restaurante.model.entity;

import com.restaurante.model.enums.StatusSessaoConsumo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidade SessaoConsumo — NÚCLEO OPERACIONAL do sistema.
 *
 * <p>Representa um evento de consumo independente e auditável.
 * Criada pelo atendente ou pelo sistema quando há operação real de consumo.
 * Nunca é reutilizada: cada evento de consumo gera uma nova instância.
 *
 * <p>Ciclo de vida:
 * <pre>
 *   ABERTA → AGUARDANDO_PAGAMENTO → ENCERRADA
 *   ABERTA →                      → ENCERRADA  (fechamento direto)
 * </pre>
 *
 * <p>Regras de negócio:
 * <ul>
 *   <li>Cada sessão possui um {@code qrCodeSessao} único — identidade da sessão.</li>
 *   <li>Cada sessão possui obrigatoriamente um {@link FundoConsumo} — criado automaticamente.</li>
 *   <li>Mesa é OPCIONAL — sessão pode existir sem mesa (eventos, bares, festivais).</li>
 *   <li>Quando mesa é fornecida: UMA sessão ABERTA por mesa por vez.</li>
 *   <li>Pedidos são vinculados à SessaoConsumo, nunca directamente à mesa.</li>
 *   <li>Encerrar a sessão não altera a mesa — o status da mesa é derivado.</li>
 * </ul>
 */
@Entity
@Table(name = "sessoes_consumo", indexes = {
    @Index(name = "idx_sessao_qr_code", columnList = "qr_code_sessao", unique = true),
    @Index(name = "idx_sessao_mesa", columnList = "mesa_id"),
    @Index(name = "idx_sessao_status", columnList = "status"),
    @Index(name = "idx_sessao_cliente", columnList = "cliente_id"),
    @Index(name = "idx_sessao_aberta_em", columnList = "aberta_em")
})
public class SessaoConsumo extends BaseEntity {

    /**
     * QR Code único desta sessão — identidade permanente da sessão.
     * Gerado automaticamente na criação. Nunca muda.
     * É usado como token de acesso ao fundo de consumo.
     */
    @Column(name = "qr_code_sessao", unique = true, length = 36, nullable = false)
    private String qrCodeSessao = UUID.randomUUID().toString();

    /**
     * Fundo de consumo desta sessão (criado automaticamente ao abrir a sessão).
     * Relação 1:1 obrigatória — o fundo é o lado inverso.
     */
    @OneToOne(mappedBy = "sessaoConsumo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private FundoConsumo fundoConsumo;

    /**
     * Mesa física associada — OPCIONAL.
     * Nula para sessões em eventos, bares de pé, festivais, etc.
     * Quando presente: apenas uma sessão ABERTA por mesa por vez.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesa_id", nullable = true)
    private Mesa mesa;

    /**
     * Unidade de atendimento que recebe os pedidos desta sessão.
     * Derivada da mesa quando presente; obrigatória quando não há mesa.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id", nullable = true)
    private UnidadeAtendimento unidadeAtendimento;

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
    private StatusSessaoConsumo status = StatusSessaoConsumo.ABERTA;

    /**
     * Flag de modo anônimo (sem identidade do cliente).
     *
     * <p>Quando {@code true}:
     * <ul>
     *   <li>{@code cliente} é null.</li>
     *   <li>{@code qrCodeSessao} é a única identificação do consumidor.</li>
     *   <li>Pós-pago não é permitido.</li>
     * </ul>
     */
    @Column(name = "modo_anonimo", nullable = false)
    private Boolean modoAnonimo = false;

    /**
     * Pedidos realizados dentro desta sessão.
     */
    @OneToMany(mappedBy = "sessaoConsumo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Pedido> pedidos = new ArrayList<>();

    /**
     * Tipo de sessão: PRE_PAGO ou POS_PAGO.
     * Define as regras de validação de saldo no Fundo de Consumo.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_sessao", nullable = false, length = 20)
    @NotNull
    // PRE_PAGO é o modo conservador e seguro por omissão — saldo nunca negativo.
    // POS_PAGO deve ser definido explicitamente no request de abertura de sessão.
    private com.restaurante.model.enums.TipoSessao tipoSessao = com.restaurante.model.enums.TipoSessao.PRE_PAGO;

    /**
     * Histórico de eventos operacionais e financeiros desta sessão.
     */
    @OneToMany(mappedBy = "sessaoConsumo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EventoSessao> eventos = new ArrayList<>();

    public SessaoConsumo() {}

    public SessaoConsumo(String qrCodeSessao, FundoConsumo fundoConsumo, Mesa mesa, UnidadeAtendimento unidadeAtendimento, Cliente cliente, Atendente aberturaPor, LocalDateTime abertaEm, LocalDateTime fechadaEm, StatusSessaoConsumo status, Boolean modoAnonimo, List<Pedido> pedidos, com.restaurante.model.enums.TipoSessao tipoSessao, List<EventoSessao> eventos) {
        this.qrCodeSessao = qrCodeSessao != null ? qrCodeSessao : UUID.randomUUID().toString();
        this.fundoConsumo = fundoConsumo;
        this.mesa = mesa;
        this.unidadeAtendimento = unidadeAtendimento;
        this.cliente = cliente;
        this.aberturaPor = aberturaPor;
        this.abertaEm = abertaEm != null ? abertaEm : LocalDateTime.now();
        this.fechadaEm = fechadaEm;
        this.status = status != null ? status : StatusSessaoConsumo.ABERTA;
        this.modoAnonimo = modoAnonimo != null ? modoAnonimo : false;
        this.pedidos = pedidos != null ? pedidos : new ArrayList<>();
        this.tipoSessao = tipoSessao != null ? tipoSessao : com.restaurante.model.enums.TipoSessao.PRE_PAGO;
        this.eventos = eventos != null ? eventos : new ArrayList<>();
    }

    public String getQrCodeSessao() { return qrCodeSessao; }
    public void setQrCodeSessao(String qrCodeSessao) { this.qrCodeSessao = qrCodeSessao; }
    
    public FundoConsumo getFundoConsumo() { return fundoConsumo; }
    public void setFundoConsumo(FundoConsumo fundoConsumo) { this.fundoConsumo = fundoConsumo; }
    
    public Mesa getMesa() { return mesa; }
    public void setMesa(Mesa mesa) { this.mesa = mesa; }
    
    public UnidadeAtendimento getUnidadeAtendimento() { return unidadeAtendimento; }
    public void setUnidadeAtendimento(UnidadeAtendimento unidadeAtendimento) { this.unidadeAtendimento = unidadeAtendimento; }
    
    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
    
    public Atendente getAberturaPor() { return aberturaPor; }
    public void setAberturaPor(Atendente aberturaPor) { this.aberturaPor = aberturaPor; }
    
    public LocalDateTime getAbertaEm() { return abertaEm; }
    public void setAbertaEm(LocalDateTime abertaEm) { this.abertaEm = abertaEm; }
    
    public LocalDateTime getFechadaEm() { return fechadaEm; }
    public void setFechadaEm(LocalDateTime fechadaEm) { this.fechadaEm = fechadaEm; }
    
    public StatusSessaoConsumo getStatus() { return status; }
    public void setStatus(StatusSessaoConsumo status) { this.status = status; }
    
    public Boolean getModoAnonimo() { return modoAnonimo; }
    public void setModoAnonimo(Boolean modoAnonimo) { this.modoAnonimo = modoAnonimo; }
    
    public List<Pedido> getPedidos() { return pedidos; }
    public void setPedidos(List<Pedido> pedidos) { this.pedidos = pedidos; }
    
    public List<EventoSessao> getEventos() { return eventos; }
    public void setEventos(List<EventoSessao> eventos) { this.eventos = eventos; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SessaoConsumo that = (SessaoConsumo) o;
        return Objects.equals(qrCodeSessao, that.qrCodeSessao);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), qrCodeSessao);
    }

    public static SessaoConsumoBuilder builder() {
        return new SessaoConsumoBuilder();
    }

    public static class SessaoConsumoBuilder {
        private String qrCodeSessao;
        private FundoConsumo fundoConsumo;
        private Mesa mesa;
        private UnidadeAtendimento unidadeAtendimento;
        private Cliente cliente;
        private Atendente aberturaPor;
        private LocalDateTime abertaEm;
        private LocalDateTime fechadaEm;
        private StatusSessaoConsumo status;
        private Boolean modoAnonimo;
        private List<Pedido> pedidos;
        private com.restaurante.model.enums.TipoSessao tipoSessao;
        private List<EventoSessao> eventos;

        SessaoConsumoBuilder() {}

        public SessaoConsumoBuilder qrCodeSessao(String qrCodeSessao) {
            this.qrCodeSessao = qrCodeSessao;
            return this;
        }

        public SessaoConsumoBuilder fundoConsumo(FundoConsumo fundoConsumo) {
            this.fundoConsumo = fundoConsumo;
            return this;
        }

        public SessaoConsumoBuilder mesa(Mesa mesa) {
            this.mesa = mesa;
            return this;
        }

        public SessaoConsumoBuilder unidadeAtendimento(UnidadeAtendimento unidadeAtendimento) {
            this.unidadeAtendimento = unidadeAtendimento;
            return this;
        }

        public SessaoConsumoBuilder cliente(Cliente cliente) {
            this.cliente = cliente;
            return this;
        }

        public SessaoConsumoBuilder aberturaPor(Atendente aberturaPor) {
            this.aberturaPor = aberturaPor;
            return this;
        }

        public SessaoConsumoBuilder abertaEm(LocalDateTime abertaEm) {
            this.abertaEm = abertaEm;
            return this;
        }

        public SessaoConsumoBuilder fechadaEm(LocalDateTime fechadaEm) {
            this.fechadaEm = fechadaEm;
            return this;
        }

        public SessaoConsumoBuilder status(StatusSessaoConsumo status) {
            this.status = status;
            return this;
        }

        public SessaoConsumoBuilder modoAnonimo(Boolean modoAnonimo) {
            this.modoAnonimo = modoAnonimo;
            return this;
        }

        public SessaoConsumoBuilder pedidos(List<Pedido> pedidos) {
            this.pedidos = pedidos;
            return this;
        }

        public SessaoConsumoBuilder tipoSessao(com.restaurante.model.enums.TipoSessao tipoSessao) {
            this.tipoSessao = tipoSessao;
            return this;
        }

        public SessaoConsumoBuilder eventos(List<EventoSessao> eventos) {
            this.eventos = eventos;
            return this;
        }

        public SessaoConsumo build() {
            return new SessaoConsumo(this.qrCodeSessao, this.fundoConsumo, this.mesa, this.unidadeAtendimento, this.cliente, this.aberturaPor, this.abertaEm, this.fechadaEm, this.status, this.modoAnonimo, this.pedidos, this.tipoSessao, this.eventos);
        }
    }

    public com.restaurante.model.enums.TipoSessao getTipoSessao() {
        return tipoSessao;
    }

    public void setTipoSessao(com.restaurante.model.enums.TipoSessao tipoSessao) {
        this.tipoSessao = tipoSessao;
    }

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
     * Returns the effective UnidadeAtendimento for routing orders.
     * Uses the session's direct unidadeAtendimento if set,
     * otherwise derives it from the associated mesa.
     */
    public UnidadeAtendimento getUnidadeAtendimentoEfetiva() {
        if (this.unidadeAtendimento != null) return this.unidadeAtendimento;
        if (this.mesa != null) return this.mesa.getUnidadeAtendimento();
        return null;
    }

    /**
     * Encerra a sessão — a mesa fica DISPONÍVEL automaticamente (status derivado).
     */
    public void encerrar() {
        this.status = StatusSessaoConsumo.ENCERRADA;
        this.fechadaEm = LocalDateTime.now();
        if (this.fundoConsumo != null) {
            this.fundoConsumo.encerrar();
        }
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
