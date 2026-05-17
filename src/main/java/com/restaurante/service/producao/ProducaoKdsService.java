package com.restaurante.service.producao;

import com.restaurante.dto.response.KdsSubPedidoResponse;
import com.restaurante.dto.response.MinhaUnidadeProducaoResponse;
import com.restaurante.dto.response.ProducaoMetricasResponse;
import com.restaurante.exception.ConflictException;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.security.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProducaoKdsService {

    private final TenantGuard tenantGuard;
    private final ProducaoScopeResolver scopeResolver;
    private final SubPedidoRepository subPedidoRepository;

    @Value("${consuma.producao.default-lookback-hours:12}")
    private int defaultLookbackHours;

    @Value("${consuma.producao.max-page-size:100}")
    private int maxPageSize;

    @Value("${consuma.producao.default-page-size:25}")
    private int defaultPageSize;

    @Value("${consuma.producao.atrasado-threshold-minutes:30}")
    private int atrasoThresholdMinutes;

    @Transactional(readOnly = true)
    public MinhaUnidadeProducaoResponse minhaUnidade() {
        return scopeResolver.minhaUnidade();
    }

    @Transactional(readOnly = true)
    public Page<KdsSubPedidoResponse> listarSubPedidosMinhaUnidade(StatusSubPedido status,
                                                                  LocalDateTime de,
                                                                  LocalDateTime ate,
                                                                  String search,
                                                                  Pageable pageable) {
        MinhaUnidadeProducaoResponse unidade = scopeResolver.minhaUnidade();
        if (unidade.modoResolucao() == MinhaUnidadeProducaoResponse.ModoResolucao.EXPLICIT_REQUIRED || unidade.unidadeProducaoId() == null) {
            throw new ConflictException("PRODUCTION_UNIT_AMBIGUOUS");
        }
        return listarSubPedidosPorUnidade(unidade.tenantId(), unidade.unidadeProducaoId(), status, de, ate, search, pageable);
    }

    @Transactional(readOnly = true)
    public Page<KdsSubPedidoResponse> listarSubPedidosTenant(Long unidadeProducaoId,
                                                            StatusSubPedido status,
                                                            LocalDateTime de,
                                                            LocalDateTime ate,
                                                            String pedidoNumero,
                                                            Pageable pageable) {
        scopeResolver.assertNotDevice();
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_OPERATOR);

        Long tenantId = scopeResolver.requireTenantId();
        Pageable effective = effectivePageable(pageable);
        LocalDateTime[] period = effectivePeriod(de, ate);

        Page<Long> idsPage = subPedidoRepository.findKdsIdsByTenantAndFilters(
                tenantId, unidadeProducaoId, status, period[0], period[1], pedidoNumero, effective
        );
        List<KdsSubPedidoResponse> content = fetchAndMap(idsPage.getContent());
        return new PageImpl<>(content, effective, idsPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ProducaoMetricasResponse metricas(Long unidadeProducaoId, LocalDateTime de, LocalDateTime ate) {
        scopeResolver.assertNotDevice();
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_OPERATOR);

        Long tenantId = scopeResolver.requireTenantId();
        LocalDateTime[] period = effectivePeriod(de, ate);
        return computeMetricas(tenantId, unidadeProducaoId, period[0], period[1]);
    }

    @Transactional(readOnly = true)
    public ProducaoMetricasResponse metricasMinhaUnidade(LocalDateTime de, LocalDateTime ate) {
        MinhaUnidadeProducaoResponse unidade = scopeResolver.minhaUnidade();
        if (unidade.modoResolucao() == MinhaUnidadeProducaoResponse.ModoResolucao.EXPLICIT_REQUIRED || unidade.unidadeProducaoId() == null) {
            throw new ConflictException("PRODUCTION_UNIT_AMBIGUOUS");
        }
        LocalDateTime[] period = effectivePeriod(de, ate);
        return computeMetricas(unidade.tenantId(), unidade.unidadeProducaoId(), period[0], period[1]);
    }

    private Page<KdsSubPedidoResponse> listarSubPedidosPorUnidade(Long tenantId,
                                                                 Long unidadeProducaoId,
                                                                 StatusSubPedido status,
                                                                 LocalDateTime de,
                                                                 LocalDateTime ate,
                                                                 String search,
                                                                 Pageable pageable) {
        Pageable effective = effectivePageable(pageable);
        LocalDateTime[] period = effectivePeriod(de, ate);

        Page<Long> idsPage = subPedidoRepository.findKdsIdsByTenantAndUnidadeAndFilters(
                tenantId, unidadeProducaoId, status, period[0], period[1], normalizeSearch(search), effective
        );
        List<KdsSubPedidoResponse> content = fetchAndMap(idsPage.getContent());
        return new PageImpl<>(content, effective, idsPage.getTotalElements());
    }

    private Pageable effectivePageable(Pageable pageable) {
        int page = pageable != null ? pageable.getPageNumber() : 0;
        int size = pageable != null ? pageable.getPageSize() : defaultPageSize;
        if (size > maxPageSize) size = maxPageSize;
        return PageRequest.of(page, size);
    }

    private LocalDateTime[] effectivePeriod(LocalDateTime de, LocalDateTime ate) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveAte = ate != null ? ate : now;
        LocalDateTime effectiveDe = de != null ? de : effectiveAte.minusHours(defaultLookbackHours);
        return new LocalDateTime[]{effectiveDe, effectiveAte};
    }

    private String normalizeSearch(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private List<KdsSubPedidoResponse> fetchAndMap(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<SubPedido> subs = subPedidoRepository.findKdsDetailsByIdIn(ids);

        Map<Long, SubPedido> byId = subs.stream().collect(Collectors.toMap(SubPedido::getId, sp -> sp, (a, b) -> a));
        return ids.stream()
                .map(byId::get)
                .filter(sp -> sp != null)
                .map(this::toKds)
                .toList();
    }

    private KdsSubPedidoResponse toKds(SubPedido sp) {
        var pedido = sp.getPedido();
        var sessao = pedido != null ? pedido.getSessaoConsumo() : null;
        var mesa = sessao != null ? sessao.getMesa() : null;

        LocalDateTime criadoEm = sp.getCreatedAt();
        LocalDateTime iniciadoEm = sp.getIniciadoEm();
        LocalDateTime prontoEm = sp.getProntoEm();

        Long sinceCreate = criadoEm != null ? Duration.between(criadoEm, LocalDateTime.now()).getSeconds() : null;
        Long inPrep = (iniciadoEm != null && prontoEm != null) ? Duration.between(iniciadoEm, prontoEm).getSeconds() : null;

        return new KdsSubPedidoResponse(
                sp.getId(),
                sp.getNumero(),
                sp.getStatus(),
                pedido != null ? pedido.getId() : null,
                pedido != null ? pedido.getNumero() : null,
                sp.getUnidadeProducao() != null ? sp.getUnidadeProducao().getId() : null,
                sp.getUnidadeProducao() != null ? sp.getUnidadeProducao().getNome() : null,
                mesa != null ? mesa.getId() : null,
                mesa != null ? mesa.getReferencia() : null,
                mesa != null ? mesa.getNumero() : null,
                criadoEm,
                iniciadoEm,
                prontoEm,
                sp.getEntregueEm(),
                sinceCreate,
                inPrep,
                sp.getTotal(),
                sp.getItens() != null ? sp.getItens().stream().map(i -> new KdsSubPedidoResponse.Item(
                        i.getId(),
                        i.getProduto() != null ? i.getProduto().getId() : null,
                        i.getProduto() != null ? i.getProduto().getNome() : null,
                        i.getQuantidade(),
                        i.getPrecoUnitario(),
                        i.getSubtotal(),
                        i.getObservacoes()
                )).toList() : List.of()
        );
    }

    private ProducaoMetricasResponse computeMetricas(Long tenantId, Long unidadeProducaoId, LocalDateTime de, LocalDateTime ate) {
        List<SubPedidoRepository.SubPedidoMetricRow> rows = subPedidoRepository.findMetricRowsByTenantAndPeriod(tenantId, unidadeProducaoId, de, ate);

        Map<String, Long> porStatus = new LinkedHashMap<>();
        rows.stream().filter(r -> r.getStatus() != null).forEach(r -> porStatus.merge(r.getStatus().name(), 1L, Long::sum));

        List<Long> ateIniciar = rows.stream()
                .filter(r -> r.getCreatedAt() != null && r.getIniciadoEm() != null)
                .map(r -> Duration.between(r.getCreatedAt(), r.getIniciadoEm()).getSeconds())
                .toList();
        List<Long> atePronto = rows.stream()
                .filter(r -> r.getCreatedAt() != null && r.getProntoEm() != null)
                .map(r -> Duration.between(r.getCreatedAt(), r.getProntoEm()).getSeconds())
                .toList();
        List<Long> prep = rows.stream()
                .filter(r -> r.getIniciadoEm() != null && r.getProntoEm() != null)
                .map(r -> Duration.between(r.getIniciadoEm(), r.getProntoEm()).getSeconds())
                .toList();

        long atrasados = rows.stream()
                .filter(r -> r.getCreatedAt() != null)
                .filter(r -> Duration.between(r.getCreatedAt(), LocalDateTime.now()).toMinutes() >= atrasoThresholdMinutes)
                .filter(r -> r.getStatus() == StatusSubPedido.PENDENTE || r.getStatus() == StatusSubPedido.EM_PREPARACAO)
                .count();

        Long avgAteIniciar = avg(ateIniciar);
        Long avgAtePronto = avg(atePronto);
        Long avgPrep = avg(prep);

        // por unidade
        Map<Long, List<SubPedidoRepository.SubPedidoMetricRow>> byUnit = rows.stream()
                .collect(Collectors.groupingBy(r -> r.getUnidadeProducaoId() != null ? r.getUnidadeProducaoId() : -1L));

        List<ProducaoMetricasResponse.UnidadeMetricas> porUnidade = byUnit.entrySet().stream()
                .filter(e -> e.getKey() != -1L)
                .map(e -> computeUnitMetricas(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(ProducaoMetricasResponse.UnidadeMetricas::unidadeProducaoId))
                .toList();

        return new ProducaoMetricasResponse(
                de,
                ate,
                rows.size(),
                porStatus,
                avgAteIniciar,
                avgAtePronto,
                avgPrep,
                atrasados,
                porUnidade
        );
    }

    private ProducaoMetricasResponse.UnidadeMetricas computeUnitMetricas(Long unidadeId, List<SubPedidoRepository.SubPedidoMetricRow> rows) {
        Map<String, Long> porStatus = new LinkedHashMap<>();
        rows.stream().filter(r -> r.getStatus() != null).forEach(r -> porStatus.merge(r.getStatus().name(), 1L, Long::sum));

        List<Long> ateIniciar = rows.stream()
                .filter(r -> r.getCreatedAt() != null && r.getIniciadoEm() != null)
                .map(r -> Duration.between(r.getCreatedAt(), r.getIniciadoEm()).getSeconds())
                .toList();
        List<Long> atePronto = rows.stream()
                .filter(r -> r.getCreatedAt() != null && r.getProntoEm() != null)
                .map(r -> Duration.between(r.getCreatedAt(), r.getProntoEm()).getSeconds())
                .toList();
        List<Long> prep = rows.stream()
                .filter(r -> r.getIniciadoEm() != null && r.getProntoEm() != null)
                .map(r -> Duration.between(r.getIniciadoEm(), r.getProntoEm()).getSeconds())
                .toList();

        long atrasados = rows.stream()
                .filter(r -> r.getCreatedAt() != null)
                .filter(r -> Duration.between(r.getCreatedAt(), LocalDateTime.now()).toMinutes() >= atrasoThresholdMinutes)
                .filter(r -> r.getStatus() == StatusSubPedido.PENDENTE || r.getStatus() == StatusSubPedido.EM_PREPARACAO)
                .count();

        String nome = rows.stream().map(SubPedidoRepository.SubPedidoMetricRow::getUnidadeProducaoNome).filter(n -> n != null).findFirst().orElse(null);
        return new ProducaoMetricasResponse.UnidadeMetricas(
                unidadeId,
                nome,
                rows.size(),
                porStatus,
                avg(ateIniciar),
                avg(atePronto),
                avg(prep),
                atrasados
        );
    }

    private Long avg(List<Long> values) {
        if (values == null || values.isEmpty()) return null;
        long sum = 0;
        for (Long v : values) sum += v;
        return sum / values.size();
    }
}

