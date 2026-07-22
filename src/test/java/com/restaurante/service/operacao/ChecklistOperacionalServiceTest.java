package com.restaurante.service.operacao;

import com.restaurante.model.entity.ChecklistOperacionalTemplate;
import com.restaurante.model.enums.ChecklistTipo;
import com.restaurante.repository.ChecklistOperacionalItemRunRepository;
import com.restaurante.repository.ChecklistOperacionalItemTemplateRepository;
import com.restaurante.repository.ChecklistOperacionalRunRepository;
import com.restaurante.repository.ChecklistOperacionalTemplateRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChecklistOperacionalServiceTest {

    @Test
    void tenantWithoutOwnTemplateFallsBackOnlyToGlobalTemplate() {
        ChecklistOperacionalTemplateRepository templates = mock(ChecklistOperacionalTemplateRepository.class);
        ChecklistOperacionalItemTemplateRepository items = mock(ChecklistOperacionalItemTemplateRepository.class);
        ChecklistOperacionalTemplate globalOpen = template(1L, ChecklistTipo.ABERTURA);
        ChecklistOperacionalTemplate globalClose = template(2L, ChecklistTipo.FECHO);

        when(templates.findByTenantIsNullAndTipoAndAtivoTrueOrderByIdAsc(ChecklistTipo.ABERTURA))
                .thenReturn(List.of(globalOpen));
        when(templates.findByTenantIsNullAndTipoAndAtivoTrueOrderByIdAsc(ChecklistTipo.FECHO))
                .thenReturn(List.of(globalClose));
        when(templates.findByTenantIdAndTipoAndAtivoTrueOrderByIdAsc(99L, ChecklistTipo.ABERTURA))
                .thenReturn(List.of());
        when(items.findByTemplateIdAndAtivoTrueOrderByOrdemAscIdAsc(1L)).thenReturn(List.of());

        ChecklistOperacionalService service = new ChecklistOperacionalService(
                templates,
                items,
                mock(ChecklistOperacionalRunRepository.class),
                mock(ChecklistOperacionalItemRunRepository.class)
        );

        var response = service.listarTemplatesAtivos(99L, ChecklistTipo.ABERTURA);

        assertThat(response).singleElement().satisfies(template ->
                assertThat(template.getId()).isEqualTo(1L));
    }

    private ChecklistOperacionalTemplate template(Long id, ChecklistTipo tipo) {
        ChecklistOperacionalTemplate template = new ChecklistOperacionalTemplate();
        template.setId(id);
        template.setTipo(tipo);
        template.setNome("Global " + tipo);
        template.setAtivo(true);
        return template;
    }
}
