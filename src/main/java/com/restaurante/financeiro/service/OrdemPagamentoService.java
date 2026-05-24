package com.restaurante.financeiro.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.FundoConsumo;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.OrdemPagamentoTipo;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.fiscal.autoissue.event.PaymentConfirmedForFiscalIssueEvent;
import com.restaurante.model.enums.FiscalAutoIssueSource;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.TransacaoFundoRepository;
import com.restaurante.service.FundoConsumoService;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrdemPagamentoService {

    private static final int ORDEM_EXPIRES_MINUTES = 30;

    private final OrdemPagamentoRepository ordemPagamentoRepository;
    private final PagamentoGatewayRepository pagamentoGatewayRepository;
    private final PedidoRepository pedidoRepository;
    private final TransacaoFundoRepository transacaoFundoRepository;
    private final FundoConsumoService fundoConsumoService;
    private final OrdemPagamentoTokenService tokenService;
    private final OperationalEventLogService operationalEventLogService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrdemPagamento criarOrdemCarregamentoFundo(Tenant tenant,
                                                     Instituicao instituicao,
                                                     UnidadeAtendimento unidadeAtendimento,
                                                     Mesa mesa,
                                                     TurnoOperacional turno,
                                                     SessaoConsumo sessao,
                                                     FundoConsumo fundo,
                                                     BigDecimal valor,
                                                     MetodoPagamentoManual metodo,
                                                     OperationalOrigem origem,
                                                     String ip,
                                                     String userAgent) {
        if (metodo == null || metodo == MetodoPagamentoManual.APPYPAY) {
            throw new BusinessException("Método inválido para ordem manual. Use CASH ou TPA.");
        }
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Valor inválido.");
        }
        if (fundo == null) throw new ResourceNotFoundException("Recurso não encontrado.");
        if (fundo.isBloqueado()) throw new BusinessException("Fundo de consumo bloqueado.");
        if (!Boolean.TRUE.equals(fundo.getAtivo())) throw new BusinessException("Fundo de consumo encerrado.");

        OrdemPagamento ordem = new OrdemPagamento();
        ordem.setTenant(tenant);
        ordem.setInstituicao(instituicao);
        ordem.setUnidadeAtendimento(unidadeAtendimento);
        ordem.setTurnoOperacional(turno);
        ordem.setTipo(OrdemPagamentoTipo.FUNDO_CONSUMO);
        ordem.setStatus(OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO);
        ordem.setMetodoSolicitado(metodo);
        ordem.setValor(valor);
        ordem.setMoeda("AOA");
        ordem.setPedido(null);
        ordem.setSessaoConsumo(sessao);
        ordem.setFundoConsumo(fundo);
        ordem.setTokenQr(tokenService.gerarTokenQr());
        ordem.setCodigoCurto(tokenService.gerarCodigoCurto());
        ordem.setExpiresAt(LocalDateTime.now().plusMinutes(ORDEM_EXPIRES_MINUTES));
        ordem.setCriadoPorOrigem(origem != null ? origem : OperationalOrigem.QR_PUBLICO);

        ordem = ordemPagamentoRepository.save(ordem);

        operationalEventLogService.logPublicEvent(
                tenant,
                instituicao,
                unidadeAtendimento,
                mesa,
                turno,
                OperationalEventType.ORDEM_PAGAMENTO_CRIADA,
                OperationalEntityType.ORDEM_PAGAMENTO,
                ordem.getId(),
                origem,
                "Ordem de carregamento criada (manual)",
                Map.of(
                        "ordemId", ordem.getId(),
                        "tipo", ordem.getTipo().name(),
                        "metodo", ordem.getMetodoSolicitado().name(),
                        "valor", ordem.getValor()
                ),
                ip,
                userAgent
        );

        return ordem;
    }

    @Transactional
    public OrdemPagamento criarOrdemPagamentoPedido(Tenant tenant,
                                                    Instituicao instituicao,
                                                    UnidadeAtendimento unidadeAtendimento,
                                                    Mesa mesa,
                                                    TurnoOperacional turno,
                                                    Pedido pedido,
                                                    MetodoPagamentoManual metodo,
                                                    OperationalOrigem origem,
                                                    String ip,
                                                    String userAgent) {
        if (pedido == null) throw new ResourceNotFoundException("Recurso não encontrado.");
        if (metodo == null || metodo == MetodoPagamentoManual.APPYPAY) {
            throw new BusinessException("Método inválido para ordem manual. Use CASH ou TPA.");
        }
        if (pedido.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO) {
            throw new BusinessException("Pedido já está pago.");
        }
        pagamentoGatewayRepository.findPagamentoConfirmadoPorPedido(pedido.getId(), TipoPagamentoFinanceiro.POS_PAGO)
                .ifPresent(p -> { throw new BusinessException("Pedido já possui pagamento confirmado."); });

        OrdemPagamento ordem = new OrdemPagamento();
        ordem.setTenant(tenant);
        ordem.setInstituicao(instituicao);
        ordem.setUnidadeAtendimento(unidadeAtendimento);
        ordem.setTurnoOperacional(turno != null ? turno : pedido.getTurnoOperacional());
        ordem.setTipo(OrdemPagamentoTipo.PEDIDO);
        ordem.setStatus(OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO);
        ordem.setMetodoSolicitado(metodo);
        ordem.setValor(pedido.getTotal());
        ordem.setMoeda("AOA");
        ordem.setPedido(pedido);
        ordem.setSessaoConsumo(pedido.getSessaoConsumo());
        ordem.setFundoConsumo(pedido.getSessaoConsumo() != null ? pedido.getSessaoConsumo().getFundoConsumo() : null);
        ordem.setTokenQr(tokenService.gerarTokenQr());
        ordem.setCodigoCurto(tokenService.gerarCodigoCurto());
        ordem.setExpiresAt(LocalDateTime.now().plusMinutes(ORDEM_EXPIRES_MINUTES));
        ordem.setCriadoPorOrigem(origem != null ? origem : OperationalOrigem.QR_PUBLICO);

        ordem = ordemPagamentoRepository.save(ordem);

        operationalEventLogService.logPublicEvent(
                tenant,
                instituicao,
                unidadeAtendimento,
                mesa,
                ordem.getTurnoOperacional(),
                OperationalEventType.ORDEM_PAGAMENTO_CRIADA,
                OperationalEntityType.ORDEM_PAGAMENTO,
                ordem.getId(),
                origem,
                "Ordem de pagamento de pedido criada (manual)",
                Map.of(
                        "ordemId", ordem.getId(),
                        "tipo", ordem.getTipo().name(),
                        "metodo", ordem.getMetodoSolicitado().name(),
                        "valor", ordem.getValor(),
                        "pedidoId", pedido.getId()
                ),
                ip,
                userAgent
        );

        return ordem;
    }

    @Transactional(readOnly = true)
    public OrdemPagamento buscarPorToken(String token) {
        return ordemPagamentoRepository.findByTokenQr(token)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
    }

    @Transactional
    public Pagamento aplicarConfirmacaoManualOrdem(OrdemPagamento ordem,
                                                  MetodoPagamentoManual metodoConfirmado,
                                                  BigDecimal valorRecebido,
                                                  String referenciaOperador,
                                                  String observacao) {
        if (ordem == null) throw new ResourceNotFoundException("Recurso não encontrado.");
        if (ordem.getStatus() == OrdemPagamentoStatus.CONFIRMADA) {
            // idempotente: já aplicado
            if (ordem.getPedido() != null) {
                return pagamentoGatewayRepository.findPagamentoConfirmadoPorPedido(ordem.getPedido().getId(), TipoPagamentoFinanceiro.POS_PAGO).orElse(null);
            }
            if (ordem.getFundoConsumo() != null) {
                // pode haver múltiplas recargas, mas esta ordem deve ser única
                return null;
            }
            return null;
        }
        if (ordem.isExpirada(LocalDateTime.now())) {
            ordem.setStatus(OrdemPagamentoStatus.EXPIRADA);
            ordemPagamentoRepository.save(ordem);
            throw new BusinessException("Ordem expirada.");
        }
        if (metodoConfirmado == null || metodoConfirmado == MetodoPagamentoManual.APPYPAY) {
            throw new BusinessException("Método confirmado inválido para manual.");
        }
        if (valorRecebido == null || valorRecebido.compareTo(ordem.getValor()) < 0) {
            throw new BusinessException("Valor recebido menor que o valor da ordem.");
        }

        ordem.setStatus(OrdemPagamentoStatus.CONFIRMADA);
        ordem.setConfirmadoEm(LocalDateTime.now());
        ordem.setReferenciaOperador(referenciaOperador);
        ordem.setObservacao(observacao);
        ordemPagamentoRepository.save(ordem);

        if (ordem.getTipo() == OrdemPagamentoTipo.FUNDO_CONSUMO) {
            FundoConsumo fundo = ordem.getFundoConsumo();
            if (fundo == null) throw new IllegalStateException("Ordem FUNDO_CONSUMO sem fundo vinculado.");
            if (fundo.isBloqueado()) throw new BusinessException("Fundo bloqueado.");

            String merchantId = "ORD-" + ordem.getId();
            if (!transacaoFundoRepository.existsByMerchantTransactionId(merchantId)) {
                // Crédito append-only idempotente por merchantTransactionId
                fundoConsumoService.creditarPorOrdemPagamento(fundo.getSessaoConsumo().getQrCodeSessao(), ordem.getValor(), merchantId,
                        "Crédito manual via " + metodoConfirmado.name() + " (ordem " + ordem.getCodigoCurto() + ")");
            }

            Pagamento pagamento = Pagamento.builder()
                    .tenant(ordem.getTenant())
                    .pedido(null)
                    .fundoConsumo(fundo)
                    .ordemPagamento(ordem)
                    .cliente(null)
                    .tipoPagamento(TipoPagamentoFinanceiro.PRE_PAGO)
                    .metodo(null)
                    .amount(ordem.getValor())
                    .status(StatusPagamentoGateway.PENDENTE)
                    .externalReference(null)
                    .observacoes("MANUAL_" + metodoConfirmado.name() + " ordemId=" + ordem.getId())
                    .build();
            pagamento.confirmar();
            return pagamentoGatewayRepository.save(pagamento);
        }

        if (ordem.getTipo() == OrdemPagamentoTipo.PEDIDO) {
            Pedido pedido = ordem.getPedido();
            if (pedido == null) throw new IllegalStateException("Ordem PEDIDO sem pedido vinculado.");

            pagamentoGatewayRepository.findPagamentoConfirmadoPorPedido(pedido.getId(), TipoPagamentoFinanceiro.POS_PAGO)
                    .ifPresent(p -> { throw new BusinessException("Pedido já possui pagamento confirmado."); });

            Pagamento pagamento = Pagamento.builder()
                    .tenant(ordem.getTenant())
                    .pedido(pedido)
                    .fundoConsumo(null)
                    .ordemPagamento(ordem)
                    .cliente(null)
                    .tipoPagamento(TipoPagamentoFinanceiro.POS_PAGO)
                    .metodo(null)
                    .amount(pedido.getTotal())
                    .status(StatusPagamentoGateway.PENDENTE)
                    .externalReference(null)
                    .observacoes("MANUAL_" + metodoConfirmado.name() + " ordemId=" + ordem.getId())
                    .build();
            pagamento.confirmar();
            pagamento = pagamentoGatewayRepository.save(pagamento);

            if (pedido.getStatusFinanceiro() != StatusFinanceiroPedido.PAGO) {
                pedido.marcarComoPago();
                pedidoRepository.save(pedido);
            }

            publishFiscalAutoIssueEventIfApplicable(ordem, pagamento, metodoConfirmado);
            return pagamento;
        }

        return null;
    }

    private void publishFiscalAutoIssueEventIfApplicable(OrdemPagamento ordem, Pagamento pagamento, MetodoPagamentoManual metodoConfirmado) {
        if (ordem == null || pagamento == null) return;
        if (pagamento.getPedido() == null) return;
        if (pagamento.getTenant() == null || pagamento.getTenant().getId() == null) return;

        FiscalAutoIssueSource source = switch (metodoConfirmado) {
            case CASH -> FiscalAutoIssueSource.CASH_MANUAL_PAYMENT;
            case TPA -> FiscalAutoIssueSource.TPA_MANUAL_PAYMENT;
            default -> FiscalAutoIssueSource.SYSTEM_BACKFILL;
        };

        eventPublisher.publishEvent(new PaymentConfirmedForFiscalIssueEvent(
                pagamento.getTenant().getId(),
                ordem.getUnidadeAtendimento() != null ? ordem.getUnidadeAtendimento().getId() : null,
                pagamento.getPedido().getId(),
                pagamento.getId(),
                ordem.getSessaoConsumo() != null ? ordem.getSessaoConsumo().getId() : null,
                ordem.getCaixaOperadorSession() != null ? ordem.getCaixaOperadorSession().getId() : null,
                source
        ));
    }
}
