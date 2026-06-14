package com.restaurante.dto.response;

import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicQrPedidoResponse {

    private Long pedidoId;
    private String numero;
    private StatusPedido statusOperacional;
    private StatusFinanceiroPedido statusFinanceiro;
    private StatusPedido operationalStatus;
    private StatusFinanceiroPedido paymentStatus;
    private String currentStep;
    private Boolean isFinal;
    private Boolean isProblem;

    private String tenantNome;
    private String instituicaoNome;
    private String unidadeAtendimentoNome;
    private String mesaReferencia;
    private Integer mesaNumero;
    private String clienteNome;
    private String clienteTelefoneMascarado;
    private PaymentMethodCode metodoPagamento;
    private String metodoPagamentoDetalhe;
    private String motivoRejeicao;
    private String ordemPagamentoToken;
    private String ordemPagamentoStatus;
    private String entidade;
    private String referencia;
    private String paymentUrl;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private LocalDateTime pagoEm;
    private LocalDateTime aceiteEm;
    private LocalDateTime rejeitadoEm;

    private BigDecimal total;
    private List<PublicQrPedidoItemResponse> itens;

    private String mensagem;
}
