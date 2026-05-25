package com.restaurante.financeiro.snapshot.evidence.dto;

import com.restaurante.financeiro.snapshot.dto.SnapshotFinanceiroExportResponse;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SnapshotFinanceiroEvidenceBundleResponse {
    private String bundleVersion;
    private LocalDateTime generatedAt;
    private EvidenceBundleGeneratedByDTO generatedBy;

    private EvidenceBundleTenantDTO tenant;
    private EvidenceBundleInstituicaoDTO instituicao;
    private EvidenceBundleUnidadeDTO unidadeAtendimento;
    private EvidenceBundleTurnoDTO turno;

    private SnapshotFinanceiroExportResponse snapshotExport;

    private List<EvidenceBundleEventoDTO> eventosOperacionais;
    private EvidenceBundlePagamentosResumoDTO pagamentosResumo;
    private OperatorCashEvidenceSectionDTO operatorCashEvidence;
    private OperatorCashDivergenceEvidenceSectionDTO operatorCashDivergenceEvidence;
    private TaxEvidenceSectionDTO taxEvidence;
    private InventoryEvidenceSectionDTO inventoryEvidence;
    private EvidenceBundleExportMetadataDTO exportMetadata;
}
