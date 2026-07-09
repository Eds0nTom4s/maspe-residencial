package com.restaurante.businesstemplate.templates;

import com.restaurante.businesstemplate.dto.BusinessTemplatePreviewResponse;
import com.restaurante.model.entity.Plano;

import java.util.ArrayList;
import java.util.List;

final class TemplatePreviewLimitCalculator {

    private TemplatePreviewLimitCalculator() {
    }

    static BusinessTemplatePreviewResponse.PlanLimitsPreview calculate(Plano plano, BusinessTemplatePreviewResponse.PlanResources resources) {
        if (plano == null || resources == null) return null;

        List<BusinessTemplatePreviewResponse.LimitLine> linhas = new ArrayList<>();

        linhas.add(line("INSTITUICOES", 0L, (long) resources.getInstituicoesCriadas(), plano.getMaxInstituicoes()));
        linhas.add(line("UNIDADES_ATENDIMENTO", 0L, (long) resources.getUnidadesAtendimentoCriadas(), plano.getMaxUnidadesAtendimento()));
        linhas.add(line("USUARIOS", 0L, (long) resources.getUsuariosCriados(), plano.getMaxUsuarios()));
        linhas.add(line("QR_CODES", 0L, (long) resources.getQrCodesCriados(), plano.getMaxQrCodes()));
        linhas.add(line("DISPOSITIVOS", 0L, (long) resources.getDispositivosCriados(), plano.getMaxDispositivos()));

        boolean precisaOverride = false;
        for (BusinessTemplatePreviewResponse.LimitLine l : linhas) {
            if (Boolean.TRUE.equals(l.getExcede())) {
                precisaOverride = true;
                break;
            }
        }

        return BusinessTemplatePreviewResponse.PlanLimitsPreview.builder()
                .linhas(linhas)
                .precisaOverride(precisaOverride)
                .build();
    }

    private static BusinessTemplatePreviewResponse.LimitLine line(String recurso, Long atual, Long planejado, Integer max) {
        long a = atual != null ? atual : 0L;
        long p = planejado != null ? planejado : 0L;
        long total = a + p;
        Long maximo = max != null ? max.longValue() : null;
        boolean excede = maximo != null && total > maximo;
        return BusinessTemplatePreviewResponse.LimitLine.builder()
                .recurso(recurso)
                .atual(a)
                .planejado(p)
                .totalApos(total)
                .maximo(maximo)
                .excede(excede)
                .build();
    }
}

