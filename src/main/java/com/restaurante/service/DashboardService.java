package com.restaurante.service;

import com.restaurante.dto.response.DashboardActivityResponse;
import com.restaurante.dto.response.DashboardStatsResponse;
import com.restaurante.dto.response.DashboardTopProductResponse;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.TransacaoFundo;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.repository.ItemPedidoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.TransacaoFundoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Service para o Dashboard Administrativo.
 *
 * <p>Todos os dados retornados são reais — lidos diretamente do banco de dados.
 * Não existem dados mockados.
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final TransacaoFundoRepository transacaoFundoRepository;

    public DashboardService(SessaoConsumoRepository sessaoConsumoRepository,
                            PedidoRepository pedidoRepository,
                            ItemPedidoRepository itemPedidoRepository,
                            TransacaoFundoRepository transacaoFundoRepository) {
        this.sessaoConsumoRepository = sessaoConsumoRepository;
        this.pedidoRepository = pedidoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.transacaoFundoRepository = transacaoFundoRepository;
    }

    /**
     * Retorna estatísticas operacionais reais do dashboard.
     */
    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        log.debug("Buscando estatísticas gerais do dashboard (dados reais)");

        // Contagens de sessões por status
        long sessoesAbertas = sessaoConsumoRepository.countByStatus(StatusSessaoConsumo.ABERTA);
        long sessoesAguardando = sessaoConsumoRepository.countByStatus(StatusSessaoConsumo.AGUARDANDO_PAGAMENTO);

        // Pedidos do dia
        long totalPedidosHoje = pedidoRepository.countPedidosHoje();
        long pedidosPendentes = pedidoRepository.countPedidosHojePorStatuses(
                List.of(StatusPedido.CRIADO, StatusPedido.EM_ANDAMENTO));

        // Receita do dia (apenas pedidos FINALIZADOS)
        BigDecimal receitaHoje = pedidoRepository.calcularReceitaHoje();
        if (receitaHoje == null) receitaHoje = BigDecimal.ZERO;

        // Clientes atendidos hoje = sessões abertas hoje com cliente identificado
        // Aproximado: usar sessões abertas + aguardando pagamento
        long clientesHoje = sessaoConsumoRepository.countSessoesHoje();

        log.debug("Stats: sessõesAbertas={}, aguardando={}, pedidosHoje={}, pendentes={}, receita={}",
                sessoesAbertas, sessoesAguardando, totalPedidosHoje, pedidosPendentes, receitaHoje);

        return DashboardStatsResponse.builder()
                .sessoesAbertas(sessoesAbertas)
                .sessoesAguardandoPagamento(sessoesAguardando)
                .totalPedidosHoje(totalPedidosHoje)
                .pedidosPendentes(pedidosPendentes)
                .receitaHoje(receitaHoje)
                .clientesAtendidosHoje(clientesHoje)
                .build();
    }

    /**
     * Retorna atividades recentes reais do sistema.
     * Combina pedidos recentes e sessões recentemente abertas.
     */
    @Transactional(readOnly = true)
    public List<DashboardActivityResponse> getRecentActivity(int limit) {
        log.debug("Buscando {} atividades recentes (dados reais)", limit);

        List<DashboardActivityResponse> activities = new ArrayList<>();

        // Pedidos recentes do dia (CRIADO e EM_ANDAMENTO) com paginação
        List<Pedido> pedidosRecentes = pedidoRepository.findPedidosDeHoje(
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();
        for (Pedido p : pedidosRecentes) {
            if (activities.size() >= limit) break;
            DashboardActivityResponse act = new DashboardActivityResponse();
            act.setTipo("PEDIDO");
            String mesa = (p.getSessaoConsumo() != null && p.getSessaoConsumo().getMesa() != null)
                    ? p.getSessaoConsumo().getMesa().getReferencia()
                    : "Sessão #" + (p.getSessaoConsumo() != null ? p.getSessaoConsumo().getId() : "?");
            act.setDescricao("Pedido " + p.getNumero() + " — " + mesa);
            act.setTimestamp(p.getCreatedAt());
            act.setDetalhes("Status: " + p.getStatus().name() +
                    " | Tipo: " + p.getTipoPagamento().getDescricao());
            activities.add(act);
        }

        // Completa com sessões abertas recentemente se ainda não atingiu o limite
        if (activities.size() < limit) {
            List<SessaoConsumo> sessoes = sessaoConsumoRepository.findByStatus(StatusSessaoConsumo.ABERTA);
            for (SessaoConsumo s : sessoes) {
                if (activities.size() >= limit) break;
                DashboardActivityResponse act = new DashboardActivityResponse();
                act.setTipo("SESSAO");
                String mesa = s.getMesa() != null ? s.getMesa().getReferencia() : "Sessão sem mesa";
                act.setDescricao("Sessão aberta — " + mesa);
                act.setTimestamp(s.getAbertaEm());
                act.setDetalhes("Status: " + s.getStatus().name() +
                        " | Tipo: " + s.getTipoSessao().name());
                activities.add(act);
            }
        }

        // 3. Pagamentos (Transações de Crédito)
        transacaoFundoRepository.findRecentPayments(org.springframework.data.domain.PageRequest.of(0, limit)).forEach(t -> {
            activities.add(new DashboardActivityResponse(
                "PAGAMENTO",
                "Pagamento recebido",
                t.getCreatedAt(),
                "Valor: " + com.restaurante.util.MoneyFormatter.format(t.getValor()) + " no fundo " + t.getFundoConsumo().getSessaoConsumo().getQrCodeSessao()
            ));
        });

        // Ordenar por timestamp decrescente e limitar
        List<DashboardActivityResponse> sortedActivities = activities.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(limit)
                .collect(Collectors.toList());

        return sortedActivities;
    }

    /**
     * Retorna produtos mais vendidos com base nos itens de pedido reais.
     * Filtra pelo período do mês corrente para dados relevantes.
     */
    @Transactional(readOnly = true)
    public List<DashboardTopProductResponse> getTopProducts(int limit) {
        log.debug("Buscando {} produtos mais vendidos (dados reais)", limit);

        // Busca desde ilício do mês atual
        LocalDateTime inicioDoPeriodo = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        List<Object[]> rows = itemPedidoRepository.findTopProdutosPorQuantidade(
                inicioDoPeriodo,
                PageRequest.of(0, Math.max(limit, 10)));

        List<DashboardTopProductResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            if (result.size() >= limit) break;
            DashboardTopProductResponse p = new DashboardTopProductResponse();
            p.setProdutoId(((Number) row[0]).longValue());
            p.setNome((String) row[1]);
            p.setCategoria(row[2] != null ? row[2].toString() : "N/A");
            p.setQuantidadeVendida(((Number) row[3]).intValue());
            p.setValorTotal(row[4] instanceof BigDecimal ? (BigDecimal) row[4] : BigDecimal.valueOf(((Number) row[4]).doubleValue()));
            result.add(p);
        }

        return result;
    }
}
