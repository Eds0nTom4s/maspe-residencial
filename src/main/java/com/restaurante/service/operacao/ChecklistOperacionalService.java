package com.restaurante.service.operacao;

import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.response.ChecklistItemRunResponse;
import com.restaurante.dto.response.ChecklistRunResponse;
import com.restaurante.dto.response.ChecklistTemplateResponse;
import com.restaurante.dto.response.ChecklistItemTemplateResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.ChecklistOperacionalItemRun;
import com.restaurante.model.entity.ChecklistOperacionalItemTemplate;
import com.restaurante.model.entity.ChecklistOperacionalRun;
import com.restaurante.model.entity.ChecklistOperacionalTemplate;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.ChecklistEscopo;
import com.restaurante.model.enums.ChecklistItemRunStatus;
import com.restaurante.model.enums.ChecklistRunStatus;
import com.restaurante.model.enums.ChecklistTipo;
import com.restaurante.model.enums.ChecklistTipoResposta;
import com.restaurante.repository.ChecklistOperacionalItemRunRepository;
import com.restaurante.repository.ChecklistOperacionalItemTemplateRepository;
import com.restaurante.repository.ChecklistOperacionalRunRepository;
import com.restaurante.repository.ChecklistOperacionalTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChecklistOperacionalService {

    public static final String TEMPLATE_ABERTURA_NOME_DEFAULT = "Checklist Abertura (default)";
    public static final String TEMPLATE_FECHO_NOME_DEFAULT = "Checklist Fecho (default)";

    private final ChecklistOperacionalTemplateRepository templateRepository;
    private final ChecklistOperacionalItemTemplateRepository itemTemplateRepository;
    private final ChecklistOperacionalRunRepository runRepository;
    private final ChecklistOperacionalItemRunRepository itemRunRepository;

    @Transactional
    public List<ChecklistTemplateResponse> listarTemplatesAtivos(Long tenantId, ChecklistTipo tipo) {
        ensureDefaultTemplatesExist();

        List<ChecklistOperacionalTemplate> templatesTenant = tenantId != null
                ? templateRepository.findByTenantIdAndTipoAndAtivoTrueOrderByIdAsc(tenantId, tipo)
                : List.of();
        List<ChecklistOperacionalTemplate> templates = !templatesTenant.isEmpty()
                ? templatesTenant
                : templateRepository.findByTenantIsNullAndTipoAndAtivoTrueOrderByIdAsc(tipo);

        List<ChecklistTemplateResponse> out = new ArrayList<>();
        for (ChecklistOperacionalTemplate t : templates) {
            out.add(toTemplateResponse(t));
        }
        return out;
    }

    @Transactional
    public ChecklistOperacionalRun validarERegistrarChecklist(
            Tenant tenant,
            TurnoOperacional turno,
            ChecklistTipo tipo,
            User executadoPor,
            DispositivoOperacional dispositivo,
            List<ChecklistItemRespostaRequest> respostas
    ) {
        ensureDefaultTemplatesExist();

        ChecklistOperacionalTemplate template = resolveTemplate(tenant, tipo);
        List<ChecklistOperacionalItemTemplate> itensTemplate = itemTemplateRepository.findByTemplateIdAndAtivoTrueOrderByOrdemAscIdAsc(template.getId());

        Map<String, ChecklistItemRespostaRequest> porCodigo = new HashMap<>();
        if (respostas != null) {
            for (ChecklistItemRespostaRequest r : respostas) {
                if (r == null || r.getCodigo() == null) continue;
                porCodigo.put(r.getCodigo().trim().toUpperCase(), r);
            }
        }

        ChecklistOperacionalRun run = new ChecklistOperacionalRun();
        run.setTenant(tenant);
        run.setTurno(turno);
        run.setTemplate(template);
        run.setTipo(tipo);
        run.setStatus(ChecklistRunStatus.EM_ANDAMENTO);
        run.setExecutadoPor(executadoPor);
        run.setDispositivo(dispositivo);
        run.setIniciadoEm(LocalDateTime.now());
        runRepository.save(run);

        boolean anyObrigatorioFalhou = false;
        for (ChecklistOperacionalItemTemplate it : itensTemplate) {
            ChecklistOperacionalItemRun itemRun = new ChecklistOperacionalItemRun();
            itemRun.setRun(run);
            itemRun.setItemTemplate(it);
            itemRun.setCodigo(it.getCodigo());
            itemRun.setDescricao(it.getDescricao());
            itemRun.setObrigatorio(it.getObrigatorio());
            itemRun.setTipoResposta(it.getTipoResposta());

            ChecklistItemRespostaRequest r = porCodigo.get(it.getCodigo().trim().toUpperCase());
            applyResposta(itemRun, r);
            itemRunRepository.save(itemRun);

            if (Boolean.TRUE.equals(itemRun.getObrigatorio()) && itemRun.getStatus() != ChecklistItemRunStatus.OK) {
                anyObrigatorioFalhou = true;
            }
        }

        run.setConcluidoEm(LocalDateTime.now());
        run.setStatus(anyObrigatorioFalhou ? ChecklistRunStatus.FALHOU : ChecklistRunStatus.CONCLUIDO);
        runRepository.save(run);

        if (anyObrigatorioFalhou) {
            throw new BusinessException("Checklist obrigatório inválido/incompleto para " + tipo + ".");
        }

        return run;
    }

    @Transactional(readOnly = true)
    public ChecklistRunResponse toRunResponse(ChecklistOperacionalRun run) {
        if (run == null) return null;
        ChecklistRunResponse r = new ChecklistRunResponse();
        r.setId(run.getId());
        r.setTipo(run.getTipo());
        r.setStatus(run.getStatus());
        r.setTemplateId(run.getTemplate() != null ? run.getTemplate().getId() : null);
        r.setExecutadoPorUserId(run.getExecutadoPor() != null ? run.getExecutadoPor().getId() : null);
        r.setDispositivoId(run.getDispositivo() != null ? run.getDispositivo().getId() : null);
        r.setIniciadoEm(run.getIniciadoEm());
        r.setConcluidoEm(run.getConcluidoEm());

        List<ChecklistOperacionalItemRun> itens = itemRunRepository.findByRunIdOrderByIdAsc(run.getId());
        List<ChecklistItemRunResponse> itensResp = new ArrayList<>();
        for (ChecklistOperacionalItemRun it : itens) {
            itensResp.add(toItemRunResponse(it));
        }
        r.setItens(itensResp);
        return r;
    }

    private ChecklistItemRunResponse toItemRunResponse(ChecklistOperacionalItemRun it) {
        ChecklistItemRunResponse r = new ChecklistItemRunResponse();
        r.setId(it.getId());
        r.setCodigo(it.getCodigo());
        r.setDescricao(it.getDescricao());
        r.setObrigatorio(it.getObrigatorio());
        r.setTipoResposta(it.getTipoResposta());
        r.setValorBoolean(it.getValorBoolean());
        r.setValorTexto(it.getValorTexto());
        r.setValorNumero(it.getValorNumero());
        r.setStatus(it.getStatus());
        r.setObservacao(it.getObservacao());
        r.setRespondidoEm(it.getRespondidoEm());
        return r;
    }

    private ChecklistTemplateResponse toTemplateResponse(ChecklistOperacionalTemplate template) {
        ChecklistTemplateResponse r = new ChecklistTemplateResponse();
        r.setId(template.getId());
        r.setTipo(template.getTipo());
        r.setNome(template.getNome());
        r.setEscopo(template.getEscopo());
        r.setAtivo(template.getAtivo());

        List<ChecklistOperacionalItemTemplate> itens = itemTemplateRepository.findByTemplateIdAndAtivoTrueOrderByOrdemAscIdAsc(template.getId());
        List<ChecklistItemTemplateResponse> itensResp = new ArrayList<>();
        for (ChecklistOperacionalItemTemplate it : itens) {
            ChecklistItemTemplateResponse ir = new ChecklistItemTemplateResponse();
            ir.setId(it.getId());
            ir.setCodigo(it.getCodigo());
            ir.setDescricao(it.getDescricao());
            ir.setObrigatorio(it.getObrigatorio());
            ir.setOrdem(it.getOrdem());
            ir.setTipoResposta(it.getTipoResposta());
            itensResp.add(ir);
        }
        r.setItens(itensResp);
        return r;
    }

    private void applyResposta(ChecklistOperacionalItemRun itemRun, ChecklistItemRespostaRequest r) {
        itemRun.setObservacao(r != null ? r.getObservacao() : null);
        itemRun.setRespondidoEm(LocalDateTime.now());
        if (r == null) {
            itemRun.setStatus(Boolean.TRUE.equals(itemRun.getObrigatorio()) ? ChecklistItemRunStatus.FALHOU : ChecklistItemRunStatus.IGNORADO);
            return;
        }

        ChecklistTipoResposta tipo = itemRun.getTipoResposta();
        if (tipo == ChecklistTipoResposta.BOOLEAN) {
            itemRun.setValorBoolean(r.getValorBoolean());
            boolean ok = Boolean.TRUE.equals(r.getValorBoolean());
            itemRun.setStatus(ok ? ChecklistItemRunStatus.OK : ChecklistItemRunStatus.FALHOU);
            return;
        }

        if (tipo == ChecklistTipoResposta.TEXTO) {
            itemRun.setValorTexto(r.getValorTexto());
            boolean ok = r.getValorTexto() != null && !r.getValorTexto().isBlank();
            itemRun.setStatus(ok ? ChecklistItemRunStatus.OK : (Boolean.TRUE.equals(itemRun.getObrigatorio()) ? ChecklistItemRunStatus.FALHOU : ChecklistItemRunStatus.IGNORADO));
            return;
        }

        if (tipo == ChecklistTipoResposta.NUMERO) {
            itemRun.setValorNumero(r.getValorNumero());
            boolean ok = r.getValorNumero() != null;
            itemRun.setStatus(ok ? ChecklistItemRunStatus.OK : (Boolean.TRUE.equals(itemRun.getObrigatorio()) ? ChecklistItemRunStatus.FALHOU : ChecklistItemRunStatus.IGNORADO));
            return;
        }

        itemRun.setStatus(ChecklistItemRunStatus.OK);
    }

    private ChecklistOperacionalTemplate resolveTemplate(Tenant tenant, ChecklistTipo tipo) {
        if (tenant != null) {
            List<ChecklistOperacionalTemplate> tenantTemplates = templateRepository.findByTenantIdAndTipoAndAtivoTrueOrderByIdAsc(tenant.getId(), tipo);
            if (!tenantTemplates.isEmpty()) return tenantTemplates.get(0);
        }
        List<ChecklistOperacionalTemplate> globals =
                templateRepository.findByTenantIsNullAndTipoAndAtivoTrueOrderByIdAsc(tipo);
        if (globals.isEmpty()) throw new BusinessException("Checklist template não encontrado para " + tipo + ".");
        return globals.get(0);
    }

    @Transactional
    public void ensureDefaultTemplatesExist() {
        if (!templateRepository.findByTenantIsNullAndTipoAndAtivoTrueOrderByIdAsc(ChecklistTipo.ABERTURA).isEmpty()
                && !templateRepository.findByTenantIsNullAndTipoAndAtivoTrueOrderByIdAsc(ChecklistTipo.FECHO).isEmpty()) {
            return;
        }

        createDefaultTemplateIfMissing(ChecklistTipo.ABERTURA, TEMPLATE_ABERTURA_NOME_DEFAULT, defaultAberturaItems());
        createDefaultTemplateIfMissing(ChecklistTipo.FECHO, TEMPLATE_FECHO_NOME_DEFAULT, defaultFechoItems());
    }

    private void createDefaultTemplateIfMissing(ChecklistTipo tipo, String nome, List<DefaultItem> items) {
        if (!templateRepository.findByTenantIsNullAndTipoAndAtivoTrueOrderByIdAsc(tipo).isEmpty()) return;

        ChecklistOperacionalTemplate t = new ChecklistOperacionalTemplate();
        t.setTenant(null);
        t.setTipo(tipo);
        t.setNome(nome);
        t.setAtivo(true);
        t.setEscopo(ChecklistEscopo.GLOBAL);
        templateRepository.save(t);

        int ordem = 0;
        for (DefaultItem di : items) {
            ChecklistOperacionalItemTemplate it = new ChecklistOperacionalItemTemplate();
            it.setTemplate(t);
            it.setCodigo(di.codigo);
            it.setDescricao(di.descricao);
            it.setObrigatorio(di.obrigatorio);
            it.setOrdem(ordem++);
            it.setTipoResposta(di.tipoResposta);
            it.setAtivo(true);
            itemTemplateRepository.save(it);
        }
    }

    private record DefaultItem(String codigo, String descricao, boolean obrigatorio, ChecklistTipoResposta tipoResposta) { }

    private List<DefaultItem> defaultAberturaItems() {
        return List.of(
                new DefaultItem("DEVICE_ONLINE", "Dispositivo/POS online e funcional?", true, ChecklistTipoResposta.BOOLEAN),
                new DefaultItem("QR_VISIVEL", "QR Code visível/posicionado para clientes?", true, ChecklistTipoResposta.BOOLEAN),
                new DefaultItem("CATALOGO_ATUALIZADO", "Catálogo/preços conferidos e atualizados?", true, ChecklistTipoResposta.BOOLEAN),
                new DefaultItem("UNIDADE_PRODUCAO_ATIVA", "Unidade(s) de produção prontas para operar?", true, ChecklistTipoResposta.BOOLEAN),
                new DefaultItem("INTERNET_OK", "Internet/conectividade ok?", false, ChecklistTipoResposta.BOOLEAN),
                new DefaultItem("OPERADOR_CONFIRMOU", "Operador confirmou a abertura?", true, ChecklistTipoResposta.BOOLEAN)
        );
    }

    private List<DefaultItem> defaultFechoItems() {
        return List.of(
                new DefaultItem("PEDIDOS_PENDENTES_VERIFICADOS", "Pedidos pendentes verificados?", true, ChecklistTipoResposta.BOOLEAN),
                new DefaultItem("PAGAMENTOS_PENDENTES_VERIFICADOS", "Pagamentos pendentes verificados?", true, ChecklistTipoResposta.BOOLEAN),
                new DefaultItem("SUBPEDIDOS_EM_ABERTO_VERIFICADOS", "Subpedidos em aberto verificados?", true, ChecklistTipoResposta.BOOLEAN),
                new DefaultItem("DISPOSITIVOS_CONFERIDOS", "Dispositivos conferidos (online/offline)?", false, ChecklistTipoResposta.BOOLEAN),
                new DefaultItem("OBSERVACAO_FECHO", "Observação de fecho (opcional)", false, ChecklistTipoResposta.TEXTO)
        );
    }
}
