package com.restaurante.service.device;

import com.restaurante.dto.response.SyncEnvelope;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.UnidadeProducao;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.RotaProducaoCategoriaRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UnidadeProducaoRepository;
import com.restaurante.repository.projection.QrAggProjection;
import com.restaurante.repository.projection.SessionOpenAggProjection;
import com.restaurante.repository.projection.SubPedidoFilaAggProjection;
import com.restaurante.repository.projection.SyncAggProjection;
import com.restaurante.security.device.DevicePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceSyncVersionService {

    private final DeviceSyncEtagService etagService;

    private final TenantRepository tenantRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final UnidadeProducaoRepository unidadeProducaoRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;

    private final CategoriaProdutoRepository categoriaProdutoRepository;
    private final ProdutoRepository produtoRepository;

    private final MesaRepository mesaRepository;
    private final SessaoConsumoRepository sessaoConsumoRepository;

    private final QrCodeOperacionalRepository qrCodeOperacionalRepository;

    private final RotaProducaoCategoriaRepository rotaProducaoCategoriaRepository;

    private final SubPedidoRepository subPedidoRepository;
    private final OperationalEventLogRepository operationalEventLogRepository;

    @Value("${consuma.sync.max-incremental-age-days:7}")
    private int maxIncrementalAgeDays;

    @Value("${consuma.sync.max-incremental-changes:1000}")
    private long maxIncrementalChanges;

    @Value("${consuma.sync.catalog.max-incremental-changes:1000}")
    private long catalogMaxIncrementalChanges;

    @Value("${consuma.sync.mesas.max-incremental-changes:500}")
    private long mesasMaxIncrementalChanges;

    @Value("${consuma.sync.qrcodes.max-incremental-changes:500}")
    private long qrCodesMaxIncrementalChanges;

    @Value("${consuma.sync.producao.max-incremental-changes:500}")
    private long producaoMaxIncrementalChanges;

    @Value("${consuma.sync.fila.max-incremental-changes:1000}")
    private long filaMaxIncrementalChanges;

    @Value("${consuma.sync.bootstrap.contract-version:v1}")
    private String bootstrapContractVersion;

    public record DomainVersion(
            String domain,
            String syncVersion,
            String etag,
            boolean reliable,
            boolean fullSyncRequired,
            SyncEnvelope.FullSyncRequiredReason fullSyncReason,
            List<SyncEnvelope.SyncWarning> warnings
    ) {}

    private boolean updatedSinceTooOld(LocalDateTime updatedSince) {
        if (updatedSince == null) return false;
        LocalDateTime min = LocalDateTime.now().minusDays(maxIncrementalAgeDays);
        return updatedSince.isBefore(min);
    }

    private SyncEnvelope.SyncWarning warning(SyncEnvelope.SyncWarningCode code, String msg) {
        return new SyncEnvelope.SyncWarning(code, msg);
    }

    private SyncAggProjection safe(SyncAggProjection p) {
        return p;
    }

    private long nz(Long v) { return v == null ? 0L : v; }

    private LocalDateTime max(LocalDateTime... values) {
        LocalDateTime m = null;
        for (LocalDateTime v : values) {
            if (v == null) continue;
            if (m == null || v.isAfter(m)) m = v;
        }
        return m;
    }

    private String seed(String domain, Object... parts) {
        StringBuilder sb = new StringBuilder(domain);
        for (Object p : parts) {
            sb.append('|').append(p == null ? "null" : p.toString());
        }
        return sb.toString();
    }

    private LocalDateTime bestTs(LocalDateTime updatedAt, LocalDateTime createdAt) {
        return updatedAt != null ? updatedAt : createdAt;
    }

    private String capabilitiesSeed(List<DeviceCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) return "";
        return capabilities.stream().map(Enum::name).sorted().reduce((a, b) -> a + "," + b).orElse("");
    }

    @Transactional(readOnly = true)
    public DomainVersion computeBootstrap(DevicePrincipal device) {
        List<SyncEnvelope.SyncWarning> warnings = new ArrayList<>();

        Tenant tenant = tenantRepository.findById(device.tenantId()).orElse(null);
        Instituicao instituicao = device.instituicaoId() != null
                ? instituicaoRepository.findByIdAndTenantId(device.instituicaoId(), device.tenantId()).orElse(null)
                : null;
        UnidadeAtendimento ua = device.unidadeAtendimentoId() != null
                ? unidadeAtendimentoRepository.findByIdAndTenantId(device.unidadeAtendimentoId(), device.tenantId()).orElse(null)
                : null;
        UnidadeProducao up = device.unidadeProducaoId() != null
                ? unidadeProducaoRepository.findByIdAndTenantId(device.unidadeProducaoId(), device.tenantId()).orElse(null)
                : null;
        DispositivoOperacional disp = device.dispositivoId() != null
                ? dispositivoOperacionalRepository.findByIdAndTenantId(device.dispositivoId(), device.tenantId()).orElse(null)
                : null;

        LocalDateTime tenantTs = tenant != null ? bestTs(tenant.getUpdatedAt(), tenant.getCreatedAt()) : null;
        LocalDateTime instTs = instituicao != null ? bestTs(instituicao.getUpdatedAt(), instituicao.getCreatedAt()) : null;
        LocalDateTime uaTs = ua != null ? bestTs(ua.getUpdatedAt(), ua.getCreatedAt()) : null;
        LocalDateTime upTs = up != null ? bestTs(up.getUpdatedAt(), up.getCreatedAt()) : null;
        LocalDateTime dispTs = disp != null ? bestTs(disp.getUpdatedAt(), disp.getCreatedAt()) : null;

        String caps = capabilitiesSeed(device.capabilities());
        String syncVersion = "bootstrap:"
                + "tenant=" + device.tenantId() + ":" + tenantTs
                + "|inst=" + device.instituicaoId() + ":" + instTs
                + "|ua=" + device.unidadeAtendimentoId() + ":" + uaTs
                + "|up=" + device.unidadeProducaoId() + ":" + upTs
                + "|dev=" + device.dispositivoId() + ":" + dispTs
                + "|status=" + device.status()
                + "|tipo=" + device.tipo()
                + "|tokenV=" + device.tokenVersion()
                + "|caps=" + caps
                + "|contract=" + bootstrapContractVersion;

        String etag = etagService.etagFor(seed("BOOTSTRAP",
                device.tenantId(),
                "tenantTs=" + tenantTs,
                "tenantEstado=" + (tenant != null ? tenant.getEstado() : null),
                "instId=" + device.instituicaoId(),
                "instTs=" + instTs,
                "uaId=" + device.unidadeAtendimentoId(),
                "uaTs=" + uaTs,
                "upId=" + device.unidadeProducaoId(),
                "upTs=" + upTs,
                "devId=" + device.dispositivoId(),
                "devTs=" + dispTs,
                "devStatus=" + device.status(),
                "devTipo=" + device.tipo(),
                "tokenV=" + device.tokenVersion(),
                "caps=" + caps,
                "contract=" + bootstrapContractVersion
        ));

        return new DomainVersion("BOOTSTRAP", syncVersion, etag, true, false, SyncEnvelope.FullSyncRequiredReason.NONE, warnings);
    }

    @Transactional(readOnly = true)
    public DomainVersion computeCatalog(DevicePrincipal device, boolean includeInactive, LocalDateTime updatedSince) {
        List<SyncEnvelope.SyncWarning> warnings = new ArrayList<>();
        boolean full = false;
        SyncEnvelope.FullSyncRequiredReason reason = SyncEnvelope.FullSyncRequiredReason.NONE;

        if (updatedSinceTooOld(updatedSince)) {
            full = true;
            reason = SyncEnvelope.FullSyncRequiredReason.CLIENT_TOO_OLD;
            warnings.add(warning(SyncEnvelope.SyncWarningCode.FULL_SYNC_RECOMMENDED, "updatedSince muito antigo; recomendado full sync."));
        }

        SyncAggProjection cat = categoriaProdutoRepository.computeSyncAgg(device.tenantId(), includeInactive);
        SyncAggProjection prod = produtoRepository.computeSyncAgg(device.tenantId(), includeInactive);

        boolean reliable = nz(cat.getNullUpdatedAtCount()) == 0 && nz(prod.getNullUpdatedAtCount()) == 0;
        LocalDateTime maxTs = max(cat.getMaxUpdatedAt(), cat.getMaxCreatedAt(), prod.getMaxUpdatedAt(), prod.getMaxCreatedAt());

        if (updatedSince != null && !reliable) {
            full = true;
            reason = SyncEnvelope.FullSyncRequiredReason.UPDATED_AT_UNRELIABLE;
            warnings.add(warning(SyncEnvelope.SyncWarningCode.UPDATED_SINCE_UNRELIABLE, "updatedAt ausente em registros relevantes; incremental pode ser incompleto."));
        }

        if (updatedSince != null && !full) {
            long changes = categoriaProdutoRepository.countByTenantIdAndUpdatedAtAfter(device.tenantId(), updatedSince)
                    + produtoRepository.countByTenantIdAndUpdatedAtAfter(device.tenantId(), updatedSince);
            long threshold = catalogMaxIncrementalChanges > 0 ? catalogMaxIncrementalChanges : maxIncrementalChanges;
            if (changes > threshold) {
                full = true;
                reason = SyncEnvelope.FullSyncRequiredReason.TOO_MANY_CHANGES;
                warnings.add(warning(SyncEnvelope.SyncWarningCode.FULL_SYNC_RECOMMENDED, "Muitas alterações desde updatedSince; recomendado full sync."));
            }
        }

        String syncVersion = "catalog:"
                + nz(cat.getCount()) + ":" + (cat.getMaxUpdatedAt() != null ? cat.getMaxUpdatedAt().truncatedTo(ChronoUnit.SECONDS) : "null") + ":"
                + nz(prod.getCount()) + ":" + (prod.getMaxUpdatedAt() != null ? prod.getMaxUpdatedAt().truncatedTo(ChronoUnit.SECONDS) : "null");

        String etag = etagService.etagFor(seed("CATALOGO",
                device.tenantId(),
                "includeInactive=" + includeInactive,
                "catCount=" + nz(cat.getCount()),
                "catMax=" + cat.getMaxUpdatedAt(),
                "prodCount=" + nz(prod.getCount()),
                "prodMax=" + prod.getMaxUpdatedAt(),
                "maxTs=" + maxTs
        ));

        return new DomainVersion("CATALOGO", syncVersion, etag, reliable, full, reason, warnings);
    }

    @Transactional(readOnly = true)
    public DomainVersion computeMesas(DevicePrincipal device, Long unidadeAtendimentoId, LocalDateTime updatedSince) {
        List<SyncEnvelope.SyncWarning> warnings = new ArrayList<>();
        boolean full = false;
        SyncEnvelope.FullSyncRequiredReason reason = SyncEnvelope.FullSyncRequiredReason.NONE;

        if (updatedSinceTooOld(updatedSince)) {
            full = true;
            reason = SyncEnvelope.FullSyncRequiredReason.CLIENT_TOO_OLD;
            warnings.add(warning(SyncEnvelope.SyncWarningCode.FULL_SYNC_RECOMMENDED, "updatedSince muito antigo; recomendado full sync."));
        }

        Long ua = unidadeAtendimentoId != null ? unidadeAtendimentoId : device.unidadeAtendimentoId();
        SyncAggProjection mesas = mesaRepository.computeSyncAgg(device.tenantId(), ua);
        SessionOpenAggProjection open = sessaoConsumoRepository.computeOpenSessionsAgg(device.tenantId(), ua);

        boolean reliable = nz(mesas.getNullUpdatedAtCount()) == 0;
        if (updatedSince != null && !reliable) {
            full = true;
            reason = SyncEnvelope.FullSyncRequiredReason.UPDATED_AT_UNRELIABLE;
            warnings.add(warning(SyncEnvelope.SyncWarningCode.UPDATED_SINCE_UNRELIABLE, "updatedAt ausente em mesas; incremental pode ser incompleto."));
        }

        if (updatedSince != null && !full) {
            long mesasChanges = ua != null
                    ? mesaRepository.countByTenantIdAndUnidadeAtendimentoIdAndUpdatedAtAfter(device.tenantId(), ua, updatedSince)
                    : mesaRepository.countByTenantIdAndUpdatedAtAfter(device.tenantId(), updatedSince);
            long sessaoChanges = ua != null
                    ? sessaoConsumoRepository.countByTenantIdAndUnidadeAtendimentoIdAndUpdatedAtAfter(device.tenantId(), ua, updatedSince)
                    : sessaoConsumoRepository.countByTenantIdAndUpdatedAtAfter(device.tenantId(), updatedSince);
            long changes = mesasChanges + sessaoChanges;
            if (changes > mesasMaxIncrementalChanges) {
                full = true;
                reason = SyncEnvelope.FullSyncRequiredReason.TOO_MANY_CHANGES;
                warnings.add(warning(SyncEnvelope.SyncWarningCode.FULL_SYNC_RECOMMENDED, "Muitas alterações desde updatedSince; recomendado full sync."));
            }
        }

        String syncVersion = "mesas:"
                + nz(mesas.getCount()) + ":" + (mesas.getMaxUpdatedAt() != null ? mesas.getMaxUpdatedAt().truncatedTo(ChronoUnit.SECONDS) : "null")
                + "|open:" + (open != null ? open.getCount() : 0L) + ":" + (open != null ? open.getMaxUltimaAtividadeEm() : null);

        String etag = etagService.etagFor(seed("MESAS",
                device.tenantId(),
                "ua=" + ua,
                "mesasCount=" + nz(mesas.getCount()),
                "mesasMax=" + mesas.getMaxUpdatedAt(),
                "openCount=" + (open != null ? open.getCount() : 0L),
                "openMaxAct=" + (open != null ? open.getMaxUltimaAtividadeEm() : null),
                "openMaxAberta=" + (open != null ? open.getMaxAbertaEm() : null)
        ));

        return new DomainVersion("MESAS", syncVersion, etag, reliable, full, reason, warnings);
    }

    @Transactional(readOnly = true)
    public DomainVersion computeQrCodes(DevicePrincipal device, LocalDateTime updatedSince) {
        List<SyncEnvelope.SyncWarning> warnings = new ArrayList<>();
        boolean full = false;
        SyncEnvelope.FullSyncRequiredReason reason = SyncEnvelope.FullSyncRequiredReason.NONE;

        if (updatedSinceTooOld(updatedSince)) {
            full = true;
            reason = SyncEnvelope.FullSyncRequiredReason.CLIENT_TOO_OLD;
            warnings.add(warning(SyncEnvelope.SyncWarningCode.FULL_SYNC_RECOMMENDED, "updatedSince muito antigo; recomendado full sync."));
        }

        Long ua = device.unidadeAtendimentoId();
        QrAggProjection qrs = qrCodeOperacionalRepository.computeSyncAgg(device.tenantId(), ua);
        boolean reliable = nz(qrs.getNullUpdatedAtCount()) == 0;
        if (updatedSince != null && !reliable) {
            full = true;
            reason = SyncEnvelope.FullSyncRequiredReason.UPDATED_AT_UNRELIABLE;
            warnings.add(warning(SyncEnvelope.SyncWarningCode.UPDATED_SINCE_UNRELIABLE, "updatedAt ausente em QR; incremental pode ser incompleto."));
        }

        if (updatedSince != null && !full) {
            long changes = ua != null
                    ? qrCodeOperacionalRepository.countByTenantIdAndUnidadeAtendimentoIdAndUpdatedAtAfter(device.tenantId(), ua, updatedSince)
                    : qrCodeOperacionalRepository.countByTenantIdAndUpdatedAtAfter(device.tenantId(), updatedSince);
            if (changes > qrCodesMaxIncrementalChanges) {
                full = true;
                reason = SyncEnvelope.FullSyncRequiredReason.TOO_MANY_CHANGES;
                warnings.add(warning(SyncEnvelope.SyncWarningCode.FULL_SYNC_RECOMMENDED, "Muitas alterações desde updatedSince; recomendado full sync."));
            }
        }

        String syncVersion = "qrcodes:" + nz(qrs.getCount()) + ":" + (qrs.getMaxUpdatedAt() != null ? qrs.getMaxUpdatedAt().truncatedTo(ChronoUnit.SECONDS) : "null");
        String etag = etagService.etagFor(seed("QRCODES",
                device.tenantId(),
                "ua=" + ua,
                "count=" + nz(qrs.getCount()),
                "maxUpdated=" + qrs.getMaxUpdatedAt(),
                "maxRevogado=" + qrs.getMaxRevogadoEm()
        ));

        return new DomainVersion("QRCODES", syncVersion, etag, reliable, full, reason, warnings);
    }

    @Transactional(readOnly = true)
    public DomainVersion computeProducao(DevicePrincipal device, LocalDateTime updatedSince) {
        List<SyncEnvelope.SyncWarning> warnings = new ArrayList<>();
        boolean full = false;
        SyncEnvelope.FullSyncRequiredReason reason = SyncEnvelope.FullSyncRequiredReason.NONE;

        if (updatedSinceTooOld(updatedSince)) {
            full = true;
            reason = SyncEnvelope.FullSyncRequiredReason.CLIENT_TOO_OLD;
            warnings.add(warning(SyncEnvelope.SyncWarningCode.FULL_SYNC_RECOMMENDED, "updatedSince muito antigo; recomendado full sync."));
        }

        SyncAggProjection unidades = unidadeProducaoRepository.computeSyncAgg(device.tenantId());
        SyncAggProjection rotas = rotaProducaoCategoriaRepository.computeSyncAgg(device.tenantId());
        boolean reliable = nz(unidades.getNullUpdatedAtCount()) == 0 && nz(rotas.getNullUpdatedAtCount()) == 0;
        if (updatedSince != null && !reliable) {
            full = true;
            reason = SyncEnvelope.FullSyncRequiredReason.UPDATED_AT_UNRELIABLE;
            warnings.add(warning(SyncEnvelope.SyncWarningCode.UPDATED_SINCE_UNRELIABLE, "updatedAt ausente em unidades/rotas; incremental pode ser incompleto."));
        }

        if (updatedSince != null && !full) {
            long changes = unidadeProducaoRepository.countByTenantIdAndUpdatedAtAfter(device.tenantId(), updatedSince)
                    + rotaProducaoCategoriaRepository.countByTenantIdAndUpdatedAtAfter(device.tenantId(), updatedSince);
            if (changes > producaoMaxIncrementalChanges) {
                full = true;
                reason = SyncEnvelope.FullSyncRequiredReason.TOO_MANY_CHANGES;
                warnings.add(warning(SyncEnvelope.SyncWarningCode.FULL_SYNC_RECOMMENDED, "Muitas alterações desde updatedSince; recomendado full sync."));
            }
        }

        String syncVersion = "producao:" + nz(unidades.getCount()) + ":" + unidades.getMaxUpdatedAt()
                + "|rotas:" + nz(rotas.getCount()) + ":" + rotas.getMaxUpdatedAt();

        String etag = etagService.etagFor(seed("PRODUCAO",
                device.tenantId(),
                "unidadesCount=" + nz(unidades.getCount()),
                "unidadesMax=" + unidades.getMaxUpdatedAt(),
                "rotasCount=" + nz(rotas.getCount()),
                "rotasMax=" + rotas.getMaxUpdatedAt()
        ));

        return new DomainVersion("PRODUCAO", syncVersion, etag, reliable, full, reason, warnings);
    }

    @Transactional(readOnly = true)
    public DomainVersion computeFila(DevicePrincipal device, Long unidadeProducaoId, com.restaurante.model.enums.StatusSubPedido status,
                                     LocalDateTime de, LocalDateTime ate, String search) {
        List<SyncEnvelope.SyncWarning> warnings = new ArrayList<>();
        boolean full = false;
        SyncEnvelope.FullSyncRequiredReason reason = SyncEnvelope.FullSyncRequiredReason.NONE;

        SubPedidoFilaAggProjection agg = subPedidoRepository.computeFilaAgg(device.tenantId(), unidadeProducaoId, status, de, ate, search);
        boolean reliable = nz(agg.getNullUpdatedAtCount()) == 0;

        LocalDateTime maxEventAt = operationalEventLogRepository.maxCreatedAtByTenantAndUnidadeProducaoAndPeriod(device.tenantId(), unidadeProducaoId, de, ate);
        LocalDateTime maxTs = max(agg.getMaxUpdatedAt(), agg.getMaxCreatedAt(), agg.getMaxIniciadoEm(), agg.getMaxProntoEm(), agg.getMaxEntregueEm(), maxEventAt);

        String syncVersion = "fila:" + nz(agg.getCount()) + ":" + (maxTs != null ? maxTs.truncatedTo(ChronoUnit.SECONDS) : "null");
        String etag = etagService.etagFor(seed("FILA",
                device.tenantId(),
                "up=" + unidadeProducaoId,
                "status=" + status,
                "de=" + de,
                "ate=" + ate,
                "search=" + search,
                "count=" + nz(agg.getCount()),
                "maxTs=" + maxTs
        ));

        return new DomainVersion("PRODUCAO_FILA", syncVersion, etag, reliable, full, reason, warnings);
    }
}
