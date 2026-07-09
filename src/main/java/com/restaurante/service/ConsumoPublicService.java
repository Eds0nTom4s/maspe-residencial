package com.restaurante.service;

import com.restaurante.dto.request.CriarCarregamentoFundoRequest;
import com.restaurante.dto.request.PublicCriarOrdemPagamentoManualPedidoRequest;
import com.restaurante.dto.response.GerirConsumoOptionsResponse;
import com.restaurante.dto.response.OrdemPagamentoResponse;
import com.restaurante.dto.response.OrdemPagamentoStatusResponse;
import com.restaurante.dto.response.SessaoConsumoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.paymentmethod.service.PaymentMethodPolicyResolutionService;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodService;
import com.restaurante.financeiro.service.OrdemPagamentoService;
import com.restaurante.model.entity.FundoConsumo;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.OrdemPagamentoTipo;
import com.restaurante.model.enums.PaymentDestination;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsumoPublicService {

    private final QrCodeOperacionalService qrCodeOperacionalService;
    private final SessaoConsumoService sessaoConsumoService;
    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final FundoConsumoService fundoConsumoService;
    private final TurnoOperacionalRepository turnoOperacionalRepository;
    private final OrdemPagamentoService ordemPagamentoService;
    private final PedidoRepository pedidoRepository;
    private final TenantPaymentMethodService tenantPaymentMethodService;
    private final PaymentMethodPolicyResolutionService policyResolutionService;
    private final PedidoPagamentoPolicy pedidoPagamentoPolicy;

    @Transactional(readOnly = true)
    public GerirConsumoOptionsResponse opcoes(String qrToken) {
        // valida QR operacional e tenant ativo
        qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(qrToken);
        GerirConsumoOptionsResponse resp = new GerirConsumoOptionsResponse();
        resp.setMensagem("Escolha como deseja gerir o seu consumo.");
        resp.setConsumoIdentificadoDisponivel(true);
        return resp;
    }

    @Transactional
    public SessaoConsumoResponse criarConsumoAnonimo(String qrToken) {
        QrCodeOperacional qr = qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(qrToken);
        if (qr.getMesa() == null && qr.getUnidadeAtendimento() == null) {
            throw new BusinessException("QR inválido para consumo (mesa/unidade ausentes).");
        }
        SessaoConsumo sessao = sessaoConsumoService.resolveOrCreateSessaoAnonima(
                qr.getTenant() != null ? qr.getTenant().getId() : null,
                qr.getInstituicao(),
                qr.getUnidadeAtendimento(),
                qr.getMesa(),
                com.restaurante.model.enums.TipoSessao.PRE_PAGO,
                true
        );
        return sessaoConsumoService.converterParaResponse(sessao);
    }

    @Transactional
    public OrdemPagamentoResponse criarOrdemCarregamentoFundo(String qrToken,
                                                             String codigoConsumo,
                                                             CriarCarregamentoFundoRequest request,
                                                             String ip,
                                                             String userAgent) {
        QrCodeOperacional qr = qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(qrToken);
        if (request == null || request.getMetodoPagamento() == null) {
            throw new BusinessException("Método de pagamento é obrigatório.");
        }
        if (request.getMetodoPagamento() == MetodoPagamentoManual.APPYPAY) {
            throw new BusinessException("AppyPay não usa confirmação manual. Use CASH ou TPA.");
        }

        SessaoConsumo sessao = sessaoConsumoRepository.findByTenantIdAndQrCodeSessao(qr.getTenant().getId(), codigoConsumo)
                .orElseThrow(() -> new ResourceNotFoundException("Consumo não encontrado."));
        FundoConsumo fundo = fundoConsumoService.buscarPorToken(sessao.getQrCodeSessao());

        TurnoOperacional turno = qr.getUnidadeAtendimento() != null
                ? turnoOperacionalRepository.findOpenByTenantAndInstituicaoAndUnidade(
                        qr.getTenant().getId(),
                        qr.getInstituicao().getId(),
                        qr.getUnidadeAtendimento().getId()
                ).orElse(null)
                : null;

        PaymentMethodCode code = request.getMetodoPagamento() == MetodoPagamentoManual.CASH ? PaymentMethodCode.CASH : PaymentMethodCode.TPA;
        policyResolutionService.validateForQr(
                qr.getTenant().getId(),
                qr.getUnidadeAtendimento() != null ? qr.getUnidadeAtendimento().getId() : null,
                code,
                PaymentDestination.FUNDO_CONSUMO,
                request.getValor()
        );
        var method = tenantPaymentMethodService.getOrThrow(qr.getTenant().getId(), code);
        if (method.isRequiresOpenTurno() && turno == null) {
            throw new BusinessException("Turno aberto é obrigatório para este método de pagamento.");
        }

        OrdemPagamento ordem = ordemPagamentoService.criarOrdemCarregamentoFundo(
                qr.getTenant(),
                qr.getInstituicao(),
                qr.getUnidadeAtendimento(),
                qr.getMesa(),
                turno,
                sessao,
                fundo,
                request.getValor(),
                request.getMetodoPagamento(),
                OperationalOrigem.QR_PUBLICO,
                ip,
                userAgent
        );

        OrdemPagamentoResponse resp = new OrdemPagamentoResponse();
        resp.setOrdemPagamentoId(ordem.getId());
        resp.setTipo(ordem.getTipo());
        resp.setStatus(ordem.getStatus());
        resp.setValor(ordem.getValor());
        resp.setMoeda(ordem.getMoeda());
        resp.setMetodoPagamento(ordem.getMetodoSolicitado());
        resp.setOrdemToken(ordem.getTokenQr());
        resp.setQrOrdemPagamento(ordem.getTokenQr());
        resp.setExpiresAt(ordem.getExpiresAt());
        resp.setMensagem("Apresente este QR/código ao operador para confirmação.");
        return resp;
    }

    @Transactional
    public OrdemPagamentoResponse criarOrdemPagamentoManualPedido(String qrToken,
                                                                 Long pedidoId,
                                                                 PublicCriarOrdemPagamentoManualPedidoRequest request,
                                                                 String ip,
                                                                 String userAgent) {
        QrCodeOperacional qr = qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(qrToken);
        if (request == null || request.getMetodoPagamento() == null) {
            throw new BusinessException("Método de pagamento é obrigatório.");
        }
        if (request.getMetodoPagamento() == MetodoPagamentoManual.APPYPAY) {
            throw new BusinessException("AppyPay não usa confirmação manual. Use CASH ou TPA.");
        }

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        if (pedido.getTenant() == null || !pedido.getTenant().getId().equals(qr.getTenant().getId())) {
            throw new ResourceNotFoundException("Pedido não encontrado.");
        }
        if (pedido.getSessaoConsumo() != null && pedido.getSessaoConsumo().getUnidadeAtendimento() != null
                && qr.getUnidadeAtendimento() != null
                && !pedido.getSessaoConsumo().getUnidadeAtendimento().getId().equals(qr.getUnidadeAtendimento().getId())) {
            throw new ResourceNotFoundException("Pedido não encontrado.");
        }
        pedidoPagamentoPolicy.assertPodeIniciarPagamento(pedido, PedidoPagamentoPolicy.PaymentFlow.PUBLIC_QR_MANUAL_ORDER);

        TurnoOperacional turno = qr.getUnidadeAtendimento() != null
                ? turnoOperacionalRepository.findOpenByTenantAndInstituicaoAndUnidade(
                qr.getTenant().getId(),
                qr.getInstituicao().getId(),
                qr.getUnidadeAtendimento().getId()
        ).orElse(null)
                : null;

        PaymentMethodCode code = request.getMetodoPagamento() == MetodoPagamentoManual.CASH ? PaymentMethodCode.CASH : PaymentMethodCode.TPA;
        policyResolutionService.validateForQr(
                qr.getTenant().getId(),
                qr.getUnidadeAtendimento() != null ? qr.getUnidadeAtendimento().getId() : null,
                code,
                PaymentDestination.PEDIDO,
                pedido.getTotal()
        );
        var method = tenantPaymentMethodService.getOrThrow(qr.getTenant().getId(), code);
        if (method.isRequiresOpenTurno() && turno == null) {
            throw new BusinessException("Turno aberto é obrigatório para este método de pagamento.");
        }

        OrdemPagamento ordem = ordemPagamentoService.criarOrdemPagamentoPedido(
                qr.getTenant(),
                qr.getInstituicao(),
                qr.getUnidadeAtendimento(),
                qr.getMesa(),
                turno,
                pedido,
                request.getMetodoPagamento(),
                OperationalOrigem.QR_PUBLICO,
                ip,
                userAgent
        );

        OrdemPagamentoResponse resp = new OrdemPagamentoResponse();
        resp.setOrdemPagamentoId(ordem.getId());
        resp.setTipo(ordem.getTipo());
        resp.setStatus(ordem.getStatus());
        resp.setValor(ordem.getValor());
        resp.setMoeda(ordem.getMoeda());
        resp.setMetodoPagamento(ordem.getMetodoSolicitado());
        resp.setOrdemToken(ordem.getTokenQr());
        resp.setQrOrdemPagamento(ordem.getTokenQr());
        resp.setExpiresAt(ordem.getExpiresAt());
        resp.setMensagem("Apresente este QR/código ao operador para confirmação.");
        return resp;
    }

    @Transactional(readOnly = true)
    public OrdemPagamentoStatusResponse statusOrdemPorToken(String tokenOrdem) {
        OrdemPagamento ordem = ordemPagamentoService.buscarPorToken(tokenOrdem);
        OrdemPagamentoStatus statusEfetivo = ordemPagamentoService.statusEfetivo(ordem);
        OrdemPagamentoStatusResponse resp = new OrdemPagamentoStatusResponse();
        resp.setStatus(statusEfetivo);
        resp.setTipo(ordem.getTipo());
        resp.setOrdemPagamentoId(ordem.getId());
        resp.setValor(ordem.getValor());
        resp.setMoeda(ordem.getMoeda());
        resp.setMetodoSolicitado(ordem.getMetodoSolicitado());
        resp.setPedidoId(ordem.getPedido() != null ? ordem.getPedido().getId() : null);

        if (ordem.getTipo() == OrdemPagamentoTipo.FUNDO_CONSUMO && ordem.getSessaoConsumo() != null) {
            resp.setCodigoConsumo(ordem.getSessaoConsumo().getQrCodeSessao());
            if (statusEfetivo == OrdemPagamentoStatus.CONFIRMADA) {
                resp.setSaldoAtual(fundoConsumoService.consultarSaldoPorToken(ordem.getSessaoConsumo().getQrCodeSessao()));
                resp.setPodeBaixarQr(true);
                resp.setMensagem("Ordem confirmada. Fundo creditado.");
            } else if (statusEfetivo == OrdemPagamentoStatus.EXPIRADA) {
                resp.setMensagem("Ordem expirada.");
            } else {
                resp.setMensagem("Aguardando confirmação do operador.");
            }
        } else if (ordem.getTipo() == OrdemPagamentoTipo.PEDIDO) {
            resp.setMensagem(statusEfetivo == OrdemPagamentoStatus.CONFIRMADA
                    ? "Ordem confirmada."
                    : statusEfetivo == OrdemPagamentoStatus.EXPIRADA
                    ? "Ordem expirada."
                    : "Aguardando confirmação do operador.");
        } else {
            resp.setMensagem("Aguardando confirmação.");
        }
        return resp;
    }
}
