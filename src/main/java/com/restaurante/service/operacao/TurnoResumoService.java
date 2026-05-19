package com.restaurante.service.operacao;

import com.restaurante.config.OperacaoProperties;
import com.restaurante.dto.response.TurnoPreFechoResponse;
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
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TurnoResumoService {

    private final OperacaoProperties operacaoProperties;
    private final PedidoRepository pedidoRepository;
    private final PagamentoGatewayRepository pagamentoRepository;
    private final SubPedidoRepository subPedidoRepository;
    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;

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

        long sessoesAbertas = sessaoConsumoRepository.countByTenantIdAndUnidadeAtendimentoIdAndStatus(
                turno.getTenant().getId(), turno.getUnidadeAtendimento().getId(), StatusSessaoConsumo.ABERTA
        );
        resp.setSessoesAbertas(sessoesAbertas);

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
        }

        // Bloqueios: pedidos não terminais
        long pedidosNaoTerminais = pedidoRepository.countNonTerminalByTenantIdAndTurnoOperacionalId(
                turno.getTenant().getId(), turno.getId()
        );
        if (pedidosNaoTerminais > 0) {
            resp.getBloqueios().add("Existem pedidos em aberto (não terminais): " + pedidosNaoTerminais);
        }

        if (sessoesAbertas > 0) {
            resp.getBloqueios().add("Existem sessões de consumo ABERTAS: " + sessoesAbertas);
        }

        long pagamentosPendentes = pagPorStatus.getOrDefault(StatusPagamentoGateway.PENDENTE.name(), 0L);
        if (pagamentosPendentes > 0) {
            resp.getAvisos().add("Existem pagamentos pendentes: " + pagamentosPendentes);
        }

        if (offline > 0) {
            resp.getAvisos().add("Existem dispositivos possivelmente offline (heartbeat stale): " + offline);
        }

        resp.setPodeFechar(resp.getBloqueios().isEmpty());
        return resp;
    }
}
