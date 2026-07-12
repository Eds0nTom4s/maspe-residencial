package com.restaurante.service.operacao;

import com.restaurante.config.OperacaoProperties;
import com.restaurante.dto.response.TurnoPreFechoResponse;
import com.restaurante.dto.response.TurnoPendenciaResponse;
import com.restaurante.financeiro.monitoramento.dto.TurnoPagamentoAlertasResponse;
import com.restaurante.financeiro.service.PagamentoPendenteQueryService;
import com.restaurante.financeiro.caixa.service.RelatorioCaixaTurnoService;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.SubPedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TurnoResumoService {

    private final OperacaoProperties operacaoProperties;
    private final PedidoRepository pedidoRepository;
    private final PagamentoGatewayRepository pagamentoRepository;
    private final SubPedidoRepository subPedidoRepository;
    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final PagamentoPendenteQueryService pagamentoPendenteQueryService;
    private final RelatorioCaixaTurnoService relatorioCaixaTurnoService;

    @Transactional(readOnly = true)
    public TurnoPreFechoResponse calcularPreFecho(TurnoOperacional turno) {
        TurnoPreFechoResponse resp = new TurnoPreFechoResponse();

        Map<String, Long> pedidosPorStatus = new java.util.HashMap<>();
        for (StatusPedido s : StatusPedido.values()) {
            long c = pedidoRepository.countByTenantIdAndTurnoOperacionalIdAndStatus(
                    turno.getTenant().getId(), turno.getId(), s
            );
            pedidosPorStatus.put(s.name(), c);
        }
        resp.setPedidosPorStatus(pedidosPorStatus);

        Map<String, Long> subPorStatus = new java.util.HashMap<>();
        for (StatusSubPedido s : StatusSubPedido.values()) {
            long c = subPedidoRepository.countByTenantIdAndPedidoTurnoOperacionalIdAndStatus(
                    turno.getTenant().getId(), turno.getId(), s
            );
            subPorStatus.put(s.name(), c);
        }
        resp.setSubPedidosPorStatus(subPorStatus);

        Map<String, Long> pagPorStatus = new java.util.HashMap<>();
        for (StatusPagamentoGateway s : StatusPagamentoGateway.values()) {
            long c = pagamentoRepository.countByTenantIdAndPedidoTurnoOperacionalIdAndStatus(
                    turno.getTenant().getId(), turno.getId(), s
            );
            pagPorStatus.put(s.name(), c);
        }
        resp.setPagamentosPorStatus(pagPorStatus);

        // Sessões operacionalmente abertas: ABERTA + AGUARDANDO_PAGAMENTO
        // ENCERRADA e EXPIRADA são excluídas — sessão auto-encerrada pelo
        // SessaoConsumoAutoClosureService não deve continuar a bloquear o pré-fecho.
        long sessoesOperacionaisAbertas = sessaoConsumoRepository
                .countByTenantIdAndUnidadeAtendimentoIdAndStatusIn(
                        turno.getTenant().getId(),
                        turno.getUnidadeAtendimento().getId(),
                        Set.of(StatusSessaoConsumo.ABERTA, StatusSessaoConsumo.AGUARDANDO_PAGAMENTO)
                );
        resp.setSessoesAbertas(sessoesOperacionaisAbertas);

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(operacaoProperties.getDeviceOfflineMinutes());
        long offline = dispositivoOperacionalRepository.countOfflineByTenantAndUnidadeAtendimento(
                turno.getTenant().getId(), turno.getUnidadeAtendimento().getId(), cutoff
        );
        resp.setDispositivosOffline(offline);

        // Bloqueios: qualquer subpedido não terminal ainda em aberto
        long subNaoTerminais = subPedidoRepository.countNonTerminalByTenantIdAndPedidoTurnoOperacionalId(
                turno.getTenant().getId(), turno.getId()
        );
        if (subNaoTerminais > 0) {
            resp.getBloqueios().add("Existem subpedidos em aberto (não terminais): " + subNaoTerminais);
            subPedidoRepository.findNonTerminalByTenantIdAndPedidoTurnoOperacionalId(
                    turno.getTenant().getId(), turno.getId()).forEach(sp -> resp.getPendencias().add(
                    TurnoPendenciaResponse.builder()
                            .categoria("SUBPEDIDO_NAO_TERMINAL")
                            .pedidoId(sp.getPedido().getId())
                            .pedidoNumero(sp.getPedido().getNumero())
                            .subPedidoId(sp.getId())
                            .status(sp.getStatus().name())
                            .acaoRecomendada("Abrir o pedido e concluir a ação operacional permitida.")
                            .build()));
        }

        // Bloqueios: pedidos não terminais
        long pedidosNaoTerminais = pedidoRepository.countNonTerminalByTenantIdAndTurnoOperacionalId(
                turno.getTenant().getId(), turno.getId()
        );
        if (pedidosNaoTerminais > 0) {
            resp.getBloqueios().add("Existem pedidos em aberto (não terminais): " + pedidosNaoTerminais);
            pedidoRepository.findNonTerminalByTenantIdAndTurnoOperacionalId(
                    turno.getTenant().getId(), turno.getId()).forEach(p -> resp.getPendencias().add(
                    TurnoPendenciaResponse.builder()
                            .categoria("PEDIDO_NAO_TERMINAL")
                            .pedidoId(p.getId())
                            .pedidoNumero(p.getNumero())
                            .status(p.getStatus().name())
                            .acaoRecomendada("Abrir o pedido e concluir ou cancelar pela máquina de estados.")
                            .build()));
        }

        // Bloqueio: sessões operacionalmente abertas (ABERTA ou AGUARDANDO_PAGAMENTO)
        if (sessoesOperacionaisAbertas > 0) {
            resp.getBloqueios().add("Existem sessões de consumo operacionalmente abertas (ABERTA/AGUARDANDO_PAGAMENTO): "
                    + sessoesOperacionaisAbertas);
            sessaoConsumoRepository.findByTenantIdAndUnidadeAtendimentoIdAndStatusIn(
                    turno.getTenant().getId(), turno.getUnidadeAtendimento().getId(),
                    Set.of(StatusSessaoConsumo.ABERTA, StatusSessaoConsumo.AGUARDANDO_PAGAMENTO)
            ).forEach(s -> resp.getPendencias().add(
                    TurnoPendenciaResponse.builder()
                            .categoria("SESSAO_OPERACIONAL_ABERTA")
                            .sessaoId(s.getId())
                            .status(s.getStatus().name())
                            .acaoRecomendada("Encerrar a sessão pelo fluxo operacional ou aguardar o auto-fecho elegível.")
                            .build()));
        }

        long pagamentosPendentes = pagPorStatus.getOrDefault(StatusPagamentoGateway.PENDENTE.name(), 0L);
        if (pagamentosPendentes > 0) {
            resp.getAvisos().add("Existem pagamentos pendentes: " + pagamentosPendentes);
        }

        if (offline > 0) {
            resp.getAvisos().add("Existem dispositivos possivelmente offline (heartbeat stale): " + offline);
        }

        // Alertas financeiros (Fase 34): anexar ao pré-fecho
        TurnoPagamentoAlertasResponse alertas = pagamentoPendenteQueryService.alertasPorTurno(turno.getId());
        resp.setAlertasFinanceiros(alertas);
        resp.setPossuiAlertasFinanceiros(alertas != null && alertas.getTotalPagamentosPendentes() > 0);
        resp.setPossuiAlertasFinanceirosCriticos(alertas != null && alertas.getTotalCriticos() > 0);

        if (alertas != null && alertas.isBloqueiaFecho()) {
            resp.getBloqueios().add("Fecho bloqueado por pagamentos críticos pendentes neste turno.");
        } else if (alertas != null && alertas.getTotalCriticos() > 0) {
            resp.getAvisos().add("Existem pagamentos críticos pendentes neste turno. Recomenda-se revisão antes do fecho.");
        }

        // Resumo financeiro reduzido (Prompt 37)
        resp.setResumoCaixa(relatorioCaixaTurnoService.gerarResumoMini(turno.getTenant().getId(), turno.getId()));

        resp.setPodeFechar(resp.getBloqueios().isEmpty());
        return resp;
    }
}
