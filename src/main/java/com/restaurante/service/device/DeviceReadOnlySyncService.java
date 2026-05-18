package com.restaurante.service.device;

import com.restaurante.dto.response.DeviceBootstrapSyncResponse;
import com.restaurante.dto.response.DeviceCatalogSyncResponse;
import com.restaurante.dto.response.DeviceMesasSyncResponse;
import com.restaurante.dto.response.DeviceProducaoSyncResponse;
import com.restaurante.dto.response.DeviceQrSyncResponse;
import com.restaurante.dto.response.SyncEnvelope;
import com.restaurante.exception.DeviceForbiddenException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.RotaProducaoCategoria;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.UnidadeProducao;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.RotaProducaoCategoriaRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UnidadeProducaoRepository;
import com.restaurante.security.device.DevicePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class DeviceReadOnlySyncService {

    private final TenantRepository tenantRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final UnidadeProducaoRepository unidadeProducaoRepository;
    private final CategoriaProdutoRepository categoriaProdutoRepository;
    private final ProdutoRepository produtoRepository;
    private final MesaRepository mesaRepository;
    private final QrCodeOperacionalRepository qrCodeOperacionalRepository;
    private final RotaProducaoCategoriaRepository rotaProducaoCategoriaRepository;
    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final DeviceSyncCursorService cursorService;

    @Value("${consuma.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Value("${consuma.sync.cursor-expiration-hours:24}")
    private int cursorExpirationHours;

    @Value("${consuma.sync.mesas.default-limit:500}")
    private int mesasDefaultLimit;

    @Value("${consuma.sync.mesas.max-limit:1000}")
    private int mesasMaxLimit;

    @Value("${consuma.sync.qrcodes.default-limit:500}")
    private int qrDefaultLimit;

    @Value("${consuma.sync.qrcodes.max-limit:1000}")
    private int qrMaxLimit;

    public void requireCapability(DevicePrincipal device, DeviceCapability capability) {
        if (device == null || device.capabilities() == null || !device.capabilities().contains(capability)) {
            throw new DeviceForbiddenException("PRODUCTION_CAPABILITY_FORBIDDEN");
        }
    }

    @Transactional(readOnly = true)
    public DeviceBootstrapSyncResponse bootstrap(DevicePrincipal device) {
        Tenant tenant = tenantRepository.findById(device.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        Instituicao instituicao = instituicaoRepository.findByIdAndTenantId(device.instituicaoId(), device.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        UnidadeAtendimento unidadeAtendimento = null;
        if (device.unidadeAtendimentoId() != null) {
            unidadeAtendimento = unidadeAtendimentoRepository.findByIdAndTenantId(device.unidadeAtendimentoId(), device.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        }

        UnidadeProducao unidadeProducao = null;
        if (device.unidadeProducaoId() != null) {
            unidadeProducao = unidadeProducaoRepository.findByIdAndTenantId(device.unidadeProducaoId(), device.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        }

        LocalDateTime now = LocalDateTime.now();
        return new DeviceBootstrapSyncResponse(
                tenant.getId(),
                tenant.getTenantCode(),
                tenant.getNome(),
                instituicao.getId(),
                instituicao.getNome(),
                unidadeAtendimento != null ? unidadeAtendimento.getId() : null,
                unidadeAtendimento != null ? unidadeAtendimento.getNome() : null,
                unidadeProducao != null ? unidadeProducao.getId() : null,
                unidadeProducao != null ? unidadeProducao.getNome() : null,
                device.dispositivoId(),
                device.dispositivoCodigo(),
                device.tipo(),
                device.status(),
                device.capabilities(),
                publicBaseUrl,
                now,
                now
        );
    }

    @Transactional(readOnly = true)
    public DeviceCatalogSyncResponse syncCatalogo(DevicePrincipal device, LocalDateTime updatedSince, boolean includeInactive) {
        requireCapability(device, DeviceCapability.SYNC_CATALOG);
        Long tenantId = device.tenantId();
        LocalDateTime now = LocalDateTime.now();

        List<CategoriaProduto> cats;
        if (updatedSince != null) {
            cats = categoriaProdutoRepository.findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(tenantId, updatedSince);
        } else {
            cats = includeInactive
                    ? categoriaProdutoRepository.findByTenantId(tenantId)
                    : categoriaProdutoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(tenantId);
        }

        List<Produto> prods;
        if (updatedSince != null) {
            prods = includeInactive
                    ? produtoRepository.findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(tenantId, updatedSince)
                    : produtoRepository.findByTenantIdAndDisponivelTrueAndAtivoTrueAndUpdatedAtAfterOrderByUpdatedAtAsc(tenantId, updatedSince);
        } else {
            prods = includeInactive
                    ? produtoRepository.findByTenantId(tenantId)
                    : produtoRepository.findByTenantIdAndDisponivelTrueAndAtivoTrue(tenantId);
        }

        List<DeviceCatalogSyncResponse.DeviceCategoriaSyncItem> catItems = cats.stream()
                .map(c -> new DeviceCatalogSyncResponse.DeviceCategoriaSyncItem(
                        c.getId(),
                        c.getNome(),
                        c.getSlug(),
                        c.getAtivo(),
                        c.getUpdatedAt()
                ))
                .toList();

        List<DeviceCatalogSyncResponse.DeviceProdutoSyncItem> prodItems = prods.stream()
                .map(p -> new DeviceCatalogSyncResponse.DeviceProdutoSyncItem(
                        p.getId(),
                        p.getCodigo(),
                        p.getNome(),
                        p.getDescricao(),
                        p.getCategoriaProduto() != null ? p.getCategoriaProduto().getId() : null,
                        p.getCategoriaProduto() != null ? p.getCategoriaProduto().getNome() : null,
                        p.getPreco() != null ? p.getPreco().toPlainString() : null,
                        p.getAtivo(),
                        p.getDisponivel(),
                        p.getUrlImagem(),
                        p.getUpdatedAt()
                ))
                .toList();

        return new DeviceCatalogSyncResponse(now, catItems, prodItems);
    }

    @Transactional(readOnly = true)
    public CatalogPageResult syncCatalogoPaged(DevicePrincipal device, LocalDateTime updatedSince, boolean includeInactive, String cursor, Integer limit) {
        requireCapability(device, DeviceCapability.SYNC_CATALOG);
        Long tenantId = device.tenantId();
        LocalDateTime now = LocalDateTime.now();

        int effectiveLimit = limit == null ? 100 : Math.min(Math.max(limit, 1), 500);

        CatalogCursor decoded = cursorService.decode(cursor, CatalogCursor.class);
        boolean cursorExpired = false;
        Long lastId = decoded != null ? decoded.lastProdutoId : null;
        if (decoded != null) {
            if (decoded.tenantId != null && !decoded.tenantId.equals(tenantId)) {
                throw new com.restaurante.exception.BusinessException("Cursor inválido.");
            }
            if (decoded.domain != null && !"CATALOGO".equals(decoded.domain)) {
                throw new com.restaurante.exception.BusinessException("Cursor inválido.");
            }
            if (decoded.issuedAt != null && decoded.issuedAt.isBefore(now.minusHours(cursorExpirationHours))) {
                cursorExpired = true;
                lastId = null; // volta ao início
            }
            if ((decoded.includeInactive != null && decoded.includeInactive != includeInactive) ||
                (decoded.updatedSince != null && updatedSince == null) ||
                (decoded.updatedSince == null && updatedSince != null) ||
                (decoded.updatedSince != null && updatedSince != null && !decoded.updatedSince.equals(updatedSince))) {
                throw new com.restaurante.exception.BusinessException("Cursor inválido.");
            }
        }

        // Categorias: mantemos simples (snapshot completo ativo, ou incremental por updatedSince)
        List<CategoriaProduto> cats = updatedSince != null
                ? categoriaProdutoRepository.findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(tenantId, updatedSince)
                : (includeInactive ? categoriaProdutoRepository.findByTenantId(tenantId) : categoriaProdutoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(tenantId));

        Pageable pageable = PageRequest.of(0, effectiveLimit + 1);
        List<Produto> chunk = produtoRepository.syncKeyset(tenantId, includeInactive, updatedSince, lastId, pageable);
        boolean hasMore = chunk.size() > effectiveLimit;
        if (hasMore) {
            chunk = chunk.subList(0, effectiveLimit);
        }
        Long nextLastId = chunk.isEmpty() ? lastId : chunk.get(chunk.size() - 1).getId();
        String nextCursor = hasMore ? cursorService.encode(new CatalogCursor("CATALOGO", tenantId, now, nextLastId, updatedSince, includeInactive)) : null;

        List<DeviceCatalogSyncResponse.DeviceCategoriaSyncItem> catItems = cats.stream()
                .map(c -> new DeviceCatalogSyncResponse.DeviceCategoriaSyncItem(
                        c.getId(),
                        c.getNome(),
                        c.getSlug(),
                        c.getAtivo(),
                        c.getUpdatedAt()
                ))
                .toList();

        List<DeviceCatalogSyncResponse.DeviceProdutoSyncItem> prodItems = chunk.stream()
                .map(p -> new DeviceCatalogSyncResponse.DeviceProdutoSyncItem(
                        p.getId(),
                        p.getCodigo(),
                        p.getNome(),
                        p.getDescricao(),
                        p.getCategoriaProduto() != null ? p.getCategoriaProduto().getId() : null,
                        p.getCategoriaProduto() != null ? p.getCategoriaProduto().getNome() : null,
                        p.getPreco() != null ? p.getPreco().toPlainString() : null,
                        p.getAtivo(),
                        p.getDisponivel(),
                        p.getUrlImagem(),
                        p.getUpdatedAt()
                ))
                .toList();

        return new CatalogPageResult(new DeviceCatalogSyncResponse(now, catItems, prodItems), hasMore, nextCursor, cursorExpired);
    }

    @Transactional(readOnly = true)
    public DeviceMesasSyncResponse syncMesas(DevicePrincipal device, LocalDateTime updatedSince, Long unidadeAtendimentoId) {
        if (device.capabilities() == null || !(device.capabilities().contains(DeviceCapability.VIEW_ORDERS) || device.capabilities().contains(DeviceCapability.SYNC_CATALOG))) {
            throw new DeviceForbiddenException("PRODUCTION_CAPABILITY_FORBIDDEN");
        }
        Long tenantId = device.tenantId();
        LocalDateTime now = LocalDateTime.now();

        Long filtroUa = unidadeAtendimentoId != null ? unidadeAtendimentoId : device.unidadeAtendimentoId();

        List<Mesa> mesas;
        if (updatedSince != null) {
            if (filtroUa != null) {
                mesas = mesaRepository.findByTenantIdAndUnidadeAtendimentoIdAndUpdatedAtAfter(tenantId, filtroUa, updatedSince);
            } else {
                mesas = mesaRepository.findByTenantIdAndUpdatedAtAfter(tenantId, updatedSince);
            }
        } else {
            mesas = filtroUa != null
                    ? mesaRepository.findByTenantIdWithFilters(tenantId, null, filtroUa, true)
                    : mesaRepository.findByTenantIdWithFilters(tenantId, null, null, true);
        }

        Set<Long> mesaIds = new HashSet<>();
        for (Mesa m : mesas) mesaIds.add(m.getId());
        Set<Long> ocupadas = mesaIds.isEmpty()
                ? Set.of()
                : new HashSet<>(sessaoConsumoRepository.findMesaIdsComSessaoAbertaByTenantAndMesaIds(tenantId, mesaIds));

        List<DeviceMesasSyncResponse.DeviceMesaSyncItem> mapped = mesas.stream()
                .map(m -> new DeviceMesasSyncResponse.DeviceMesaSyncItem(
                        m.getId(),
                        m.getUnidadeAtendimento() != null ? m.getUnidadeAtendimento().getId() : null,
                        m.getNumero(),
                        m.getReferencia(),
                        ocupadas.contains(m.getId()) ? "OCUPADA" : "DISPONIVEL",
                        m.getAtiva(),
                        null,
                        null,
                        m.getUpdatedAt()
                ))
                .toList();

        return new DeviceMesasSyncResponse(now, mapped);
    }

    public record MesasPageResult(DeviceMesasSyncResponse data, boolean hasMore, String nextCursor, boolean cursorExpired) {}

    public static final class MesasCursor {
        public String domain;
        public Long tenantId;
        public LocalDateTime issuedAt;
        public Long lastMesaId;
        public LocalDateTime updatedSince;
        public Long unidadeAtendimentoId;
        public MesasCursor() {}
        public MesasCursor(String domain, Long tenantId, LocalDateTime issuedAt, Long lastMesaId, LocalDateTime updatedSince, Long unidadeAtendimentoId) {
            this.domain = domain;
            this.tenantId = tenantId;
            this.issuedAt = issuedAt != null ? issuedAt.truncatedTo(ChronoUnit.SECONDS) : null;
            this.lastMesaId = lastMesaId;
            this.updatedSince = updatedSince != null ? updatedSince.truncatedTo(ChronoUnit.SECONDS) : null;
            this.unidadeAtendimentoId = unidadeAtendimentoId;
        }
    }

    @Transactional(readOnly = true)
    public MesasPageResult syncMesasPaged(DevicePrincipal device, LocalDateTime updatedSince, Long unidadeAtendimentoId, String cursor, Integer limit) {
        if (device.capabilities() == null || !(device.capabilities().contains(DeviceCapability.VIEW_ORDERS) || device.capabilities().contains(DeviceCapability.SYNC_CATALOG))) {
            throw new DeviceForbiddenException("PRODUCTION_CAPABILITY_FORBIDDEN");
        }
        Long tenantId = device.tenantId();
        LocalDateTime now = LocalDateTime.now();

        int effectiveLimit = limit == null ? mesasDefaultLimit : Math.min(Math.max(limit, 1), mesasMaxLimit);

        MesasCursor decoded = cursorService.decode(cursor, MesasCursor.class);
        boolean cursorExpired = false;
        Long lastId = decoded != null ? decoded.lastMesaId : null;

        Long filtroUa = unidadeAtendimentoId != null ? unidadeAtendimentoId : device.unidadeAtendimentoId();

        if (decoded != null) {
            if (decoded.tenantId != null && !decoded.tenantId.equals(tenantId)) {
                throw new com.restaurante.exception.BusinessException("Cursor inválido.");
            }
            if (decoded.domain != null && !"MESAS".equals(decoded.domain)) {
                throw new com.restaurante.exception.BusinessException("Cursor inválido.");
            }
            if (decoded.issuedAt != null && decoded.issuedAt.isBefore(now.minusHours(cursorExpirationHours))) {
                cursorExpired = true;
                lastId = null;
            }
            if ((decoded.updatedSince != null && updatedSince == null) ||
                (decoded.updatedSince == null && updatedSince != null) ||
                (decoded.updatedSince != null && updatedSince != null && !decoded.updatedSince.equals(updatedSince.truncatedTo(ChronoUnit.SECONDS))) ||
                (decoded.unidadeAtendimentoId != null && filtroUa == null) ||
                (decoded.unidadeAtendimentoId == null && filtroUa != null) ||
                (decoded.unidadeAtendimentoId != null && filtroUa != null && !decoded.unidadeAtendimentoId.equals(filtroUa))) {
                throw new com.restaurante.exception.BusinessException("Cursor inválido.");
            }
        }

        Pageable pageable = PageRequest.of(0, effectiveLimit + 1);
        List<Mesa> chunk = mesaRepository.syncKeyset(tenantId, filtroUa, updatedSince, lastId, pageable);
        boolean hasMore = chunk.size() > effectiveLimit;
        if (hasMore) {
            chunk = chunk.subList(0, effectiveLimit);
        }

        Set<Long> mesaIds = new HashSet<>();
        for (Mesa m : chunk) mesaIds.add(m.getId());
        Set<Long> ocupadas = mesaIds.isEmpty()
                ? Set.of()
                : new HashSet<>(sessaoConsumoRepository.findMesaIdsComSessaoAbertaByTenantAndMesaIds(tenantId, mesaIds));

        List<DeviceMesasSyncResponse.DeviceMesaSyncItem> mapped = chunk.stream()
                .map(m -> new DeviceMesasSyncResponse.DeviceMesaSyncItem(
                        m.getId(),
                        m.getUnidadeAtendimento() != null ? m.getUnidadeAtendimento().getId() : null,
                        m.getNumero(),
                        m.getReferencia(),
                        ocupadas.contains(m.getId()) ? "OCUPADA" : "DISPONIVEL",
                        m.getAtiva(),
                        null,
                        null,
                        m.getUpdatedAt()
                ))
                .toList();

        DeviceMesasSyncResponse resp = new DeviceMesasSyncResponse(now, mapped);
        Long nextLastId = chunk.isEmpty() ? lastId : chunk.get(chunk.size() - 1).getId();
        String nextCursor = hasMore ? cursorService.encode(new MesasCursor("MESAS", tenantId, now, nextLastId, updatedSince, filtroUa)) : null;

        return new MesasPageResult(resp, hasMore, nextCursor, cursorExpired);
    }

    @Transactional(readOnly = true)
    public DeviceQrSyncResponse syncQrCodes(DevicePrincipal device, LocalDateTime updatedSince) {
        if (device.capabilities() == null || !(device.capabilities().contains(DeviceCapability.VIEW_ORDERS) || device.capabilities().contains(DeviceCapability.SYNC_CATALOG))) {
            throw new DeviceForbiddenException("PRODUCTION_CAPABILITY_FORBIDDEN");
        }
        Long tenantId = device.tenantId();
        LocalDateTime now = LocalDateTime.now();

        List<QrCodeOperacional> qrs;
        if (updatedSince != null) {
            if (device.unidadeAtendimentoId() != null) {
                qrs = qrCodeOperacionalRepository.findByTenantIdAndUnidadeAtendimentoIdAndAtivoTrueAndRevogadoFalseAndUpdatedAtAfter(
                        tenantId, device.unidadeAtendimentoId(), updatedSince
                );
            } else {
                qrs = qrCodeOperacionalRepository.findByTenantIdAndAtivoTrueAndRevogadoFalseAndUpdatedAtAfter(tenantId, updatedSince);
            }
        } else {
            qrs = device.unidadeAtendimentoId() != null
                    ? qrCodeOperacionalRepository.findByTenantIdAndUnidadeAtendimentoIdAndAtivoTrueAndRevogadoFalse(tenantId, device.unidadeAtendimentoId())
                    : qrCodeOperacionalRepository.findByTenantIdAndAtivoTrueAndRevogadoFalse(tenantId);
        }

        List<DeviceQrSyncResponse.DeviceQrSyncItem> items = qrs.stream()
                .map(q -> new DeviceQrSyncResponse.DeviceQrSyncItem(
                        q.getId(),
                        q.getTipo(),
                        q.getNome(),
                        q.getToken(),
                        publicBaseUrl + "/q/" + q.getToken(),
                        q.getInstituicao() != null ? q.getInstituicao().getId() : null,
                        q.getUnidadeAtendimento() != null ? q.getUnidadeAtendimento().getId() : null,
                        q.getMesa() != null ? q.getMesa().getId() : null,
                        q.getAtivo(),
                        q.getRevogado(),
                        q.getUpdatedAt()
                ))
                .toList();

        return new DeviceQrSyncResponse(now, items);
    }

    public record QrPageResult(DeviceQrSyncResponse data, boolean hasMore, String nextCursor, boolean cursorExpired) {}

    public static final class QrCursor {
        public String domain;
        public Long tenantId;
        public LocalDateTime issuedAt;
        public Long lastQrId;
        public LocalDateTime updatedSince;
        public Long unidadeAtendimentoId;
        public QrCursor() {}
        public QrCursor(String domain, Long tenantId, LocalDateTime issuedAt, Long lastQrId, LocalDateTime updatedSince, Long unidadeAtendimentoId) {
            this.domain = domain;
            this.tenantId = tenantId;
            this.issuedAt = issuedAt != null ? issuedAt.truncatedTo(ChronoUnit.SECONDS) : null;
            this.lastQrId = lastQrId;
            this.updatedSince = updatedSince != null ? updatedSince.truncatedTo(ChronoUnit.SECONDS) : null;
            this.unidadeAtendimentoId = unidadeAtendimentoId;
        }
    }

    @Transactional(readOnly = true)
    public QrPageResult syncQrCodesPaged(DevicePrincipal device, LocalDateTime updatedSince, String cursor, Integer limit) {
        if (device.capabilities() == null || !(device.capabilities().contains(DeviceCapability.VIEW_ORDERS) || device.capabilities().contains(DeviceCapability.SYNC_CATALOG))) {
            throw new DeviceForbiddenException("PRODUCTION_CAPABILITY_FORBIDDEN");
        }
        Long tenantId = device.tenantId();
        LocalDateTime now = LocalDateTime.now();

        int effectiveLimit = limit == null ? qrDefaultLimit : Math.min(Math.max(limit, 1), qrMaxLimit);

        QrCursor decoded = cursorService.decode(cursor, QrCursor.class);
        boolean cursorExpired = false;
        Long lastId = decoded != null ? decoded.lastQrId : null;
        Long filtroUa = device.unidadeAtendimentoId();

        if (decoded != null) {
            if (decoded.tenantId != null && !decoded.tenantId.equals(tenantId)) {
                throw new com.restaurante.exception.BusinessException("Cursor inválido.");
            }
            if (decoded.domain != null && !"QRCODES".equals(decoded.domain)) {
                throw new com.restaurante.exception.BusinessException("Cursor inválido.");
            }
            if (decoded.issuedAt != null && decoded.issuedAt.isBefore(now.minusHours(cursorExpirationHours))) {
                cursorExpired = true;
                lastId = null;
            }
            if ((decoded.updatedSince != null && updatedSince == null) ||
                (decoded.updatedSince == null && updatedSince != null) ||
                (decoded.updatedSince != null && updatedSince != null && !decoded.updatedSince.equals(updatedSince.truncatedTo(ChronoUnit.SECONDS))) ||
                (decoded.unidadeAtendimentoId != null && filtroUa == null) ||
                (decoded.unidadeAtendimentoId == null && filtroUa != null) ||
                (decoded.unidadeAtendimentoId != null && filtroUa != null && !decoded.unidadeAtendimentoId.equals(filtroUa))) {
                throw new com.restaurante.exception.BusinessException("Cursor inválido.");
            }
        }

        Pageable pageable = PageRequest.of(0, effectiveLimit + 1);
        List<QrCodeOperacional> chunk = qrCodeOperacionalRepository.syncKeyset(tenantId, filtroUa, updatedSince, lastId, pageable);
        boolean hasMore = chunk.size() > effectiveLimit;
        if (hasMore) {
            chunk = chunk.subList(0, effectiveLimit);
        }
        Long nextLastId = chunk.isEmpty() ? lastId : chunk.get(chunk.size() - 1).getId();
        String nextCursor = hasMore ? cursorService.encode(new QrCursor("QRCODES", tenantId, now, nextLastId, updatedSince, filtroUa)) : null;

        List<DeviceQrSyncResponse.DeviceQrSyncItem> items = chunk.stream()
                .map(q -> new DeviceQrSyncResponse.DeviceQrSyncItem(
                        q.getId(),
                        q.getTipo(),
                        q.getNome(),
                        q.getToken(),
                        publicBaseUrl + "/q/" + q.getToken(),
                        q.getInstituicao() != null ? q.getInstituicao().getId() : null,
                        q.getUnidadeAtendimento() != null ? q.getUnidadeAtendimento().getId() : null,
                        q.getMesa() != null ? q.getMesa().getId() : null,
                        q.getAtivo(),
                        q.getRevogado(),
                        q.getUpdatedAt()
                ))
                .toList();

        DeviceQrSyncResponse resp = new DeviceQrSyncResponse(now, items);
        return new QrPageResult(resp, hasMore, nextCursor, cursorExpired);
    }

    @Transactional(readOnly = true)
    public DeviceProducaoSyncResponse syncProducao(DevicePrincipal device, LocalDateTime updatedSince) {
        requireCapability(device, DeviceCapability.VIEW_PRODUCTION);
        Long tenantId = device.tenantId();
        LocalDateTime now = LocalDateTime.now();

        List<UnidadeProducao> unidades = updatedSince != null
                ? unidadeProducaoRepository.findByTenantIdAndAtivoTrueAndUpdatedAtAfterOrderByUpdatedAtAsc(tenantId, updatedSince)
                : unidadeProducaoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(tenantId);

        List<RotaProducaoCategoria> rotas = updatedSince != null
                ? rotaProducaoCategoriaRepository.findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(tenantId, updatedSince)
                : rotaProducaoCategoriaRepository.findByTenantIdAndAtivoTrue(tenantId);

        List<DeviceProducaoSyncResponse.DeviceUnidadeProducaoSyncItem> unidadeItems = unidades.stream()
                .map(u -> new DeviceProducaoSyncResponse.DeviceUnidadeProducaoSyncItem(
                        u.getId(),
                        u.getNome(),
                        u.getCodigo(),
                        u.getTipo(),
                        u.getInstituicao() != null ? u.getInstituicao().getId() : null,
                        u.getUnidadeAtendimento() != null ? u.getUnidadeAtendimento().getId() : null,
                        u.getAtivo(),
                        u.getUpdatedAt()
                ))
                .toList();

        List<DeviceProducaoSyncResponse.DeviceRotaProducaoSyncItem> rotaItems = rotas.stream()
                .map(r -> new DeviceProducaoSyncResponse.DeviceRotaProducaoSyncItem(
                        r.getId(),
                        r.getCategoriaProduto() != null ? r.getCategoriaProduto().getId() : null,
                        r.getUnidadeProducao() != null ? r.getUnidadeProducao().getId() : null,
                        r.getAtivo(),
                        r.getPrioridade(),
                        r.getUpdatedAt()
                ))
                .toList();

        return new DeviceProducaoSyncResponse(now, unidadeItems, rotaItems);
    }

    public record CatalogPageResult(DeviceCatalogSyncResponse data, boolean hasMore, String nextCursor, boolean cursorExpired) {}

    public static final class CatalogCursor {
        public String domain;
        public Long tenantId;
        public LocalDateTime issuedAt;
        public Long lastProdutoId;
        public LocalDateTime updatedSince;
        public Boolean includeInactive;
        public CatalogCursor() {}
        public CatalogCursor(String domain, Long tenantId, LocalDateTime issuedAt, Long lastProdutoId, LocalDateTime updatedSince, Boolean includeInactive) {
            this.domain = domain;
            this.tenantId = tenantId;
            this.issuedAt = issuedAt != null ? issuedAt.truncatedTo(ChronoUnit.SECONDS) : null;
            this.lastProdutoId = lastProdutoId;
            this.updatedSince = updatedSince;
            this.includeInactive = includeInactive;
        }
    }
}
