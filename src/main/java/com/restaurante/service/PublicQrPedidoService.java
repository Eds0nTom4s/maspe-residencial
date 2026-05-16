package com.restaurante.service;

import com.restaurante.dto.request.PublicQrPedidoItemRequest;
import com.restaurante.dto.request.PublicQrPedidoRequest;
import com.restaurante.dto.response.PublicQrPedidoItemResponse;
import com.restaurante.dto.response.PublicQrPedidoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TipoPagamentoPedido;
import com.restaurante.model.enums.TipoSessao;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicQrPedidoService {

    private final QrCodeOperacionalService qrCodeOperacionalService;
    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final SubPedidoService subPedidoService;
    private final FundoConsumoService fundoConsumoService;
    private final SessaoConsumoService sessaoConsumoService;

    @Transactional
    public PublicQrPedidoResponse criarPedidoPublicoPorQrToken(String token, PublicQrPedidoRequest request) {
        QrCodeOperacional qr = qrCodeOperacionalService.resolverOperacionalAtivoParaOperacao(token);

        Tenant tenant = qr.getTenant();
        Instituicao instituicao = qr.getInstituicao();
        UnidadeAtendimento unidadeAtendimento = unidadeAtendimentoEfetiva(qr);
        Mesa mesa = qr.getMesa();

        List<Produto> produtos = validarECarregarProdutosDoTenant(tenant.getId(), request.getItens());

        SessaoConsumo sessao = resolverOuCriarSessaoMinima(qr, instituicao, unidadeAtendimento, mesa);

        Pedido pedido = new Pedido();
        pedido.setTenant(tenant);
        pedido.setNumero(gerarNumeroPedido());
        pedido.setSessaoConsumo(sessao);
        pedido.setStatus(StatusPedido.CRIADO);
        pedido.setStatusFinanceiro(StatusFinanceiroPedido.NAO_PAGO);
        pedido.setTipoPagamento(TipoPagamentoPedido.POS_PAGO);
        pedido.setObservacoes(request.getObservacao());

        Map<Cozinha, List<PublicQrPedidoItemRequest>> requestsPorCozinha = agruparItensPorCozinha(unidadeAtendimento.getId(), request.getItens(), produtos);

        pedido = pedidoRepository.save(pedido);

        List<PublicQrPedidoItemResponse> itensResponse = new ArrayList<>();
        int contadorSubPedido = 1;

        for (Map.Entry<Cozinha, List<PublicQrPedidoItemRequest>> entry : requestsPorCozinha.entrySet()) {
            Cozinha cozinha = entry.getKey();
            List<PublicQrPedidoItemRequest> itensReq = entry.getValue();

            SubPedido subPedido = SubPedido.builder()
                    .numero(pedido.getNumero() + "-" + contadorSubPedido)
                    .pedido(pedido)
                    .cozinha(cozinha)
                    .unidadeAtendimento(unidadeAtendimento)
                    .status(StatusSubPedido.CRIADO)
                    .build();
            subPedido.setTenant(tenant);
            contadorSubPedido++;

            for (PublicQrPedidoItemRequest itemReq : itensReq) {
                Produto produto = produtos.stream()
                        .filter(p -> p.getId().equals(itemReq.getProdutoId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException("Produto inválido ou indisponível."));

                ItemPedido item = ItemPedido.builder()
                        .pedido(pedido)
                        .subPedido(subPedido)
                        .produto(produto)
                        .quantidade(itemReq.getQuantidade())
                        .precoUnitario(produto.getPreco())
                        .observacoes(itemReq.getObservacao())
                        .build();
                item.setTenant(tenant);
                item.calcularSubtotal();

                pedido.adicionarItem(item);
                subPedido.adicionarItem(item);

                itensResponse.add(PublicQrPedidoItemResponse.builder()
                        .produtoId(produto.getId())
                        .nome(produto.getNome())
                        .quantidade(itemReq.getQuantidade())
                        .precoUnitario(produto.getPreco())
                        .subtotal(item.getSubtotal())
                        .build());
            }

            subPedido.calcularTotal();
            pedido.getSubPedidos().add(subPedido);
        }

        pedido.calcularTotal();
        pedidoRepository.save(pedido);

        sessaoConsumoService.registrarAtividade(sessao, "Pedido público por QR criado: " + pedido.getNumero());

        return PublicQrPedidoResponse.builder()
                .pedidoId(pedido.getId())
                .numero(pedido.getNumero())
                .statusOperacional(pedido.getStatus())
                .statusFinanceiro(pedido.getStatusFinanceiro())
                .tenantNome(tenant.getNome())
                .instituicaoNome(instituicao.getNome())
                .unidadeAtendimentoNome(unidadeAtendimento.getNome())
                .mesaReferencia(mesa != null ? mesa.getReferencia() : null)
                .mesaNumero(mesa != null ? mesa.getNumero() : null)
                .total(pedido.getTotal())
                .itens(itensResponse)
                .mensagem("Pedido criado com sucesso")
                .build();
    }

    private UnidadeAtendimento unidadeAtendimentoEfetiva(QrCodeOperacional qr) {
        if (qr.getUnidadeAtendimento() != null) return qr.getUnidadeAtendimento();
        if (qr.getMesa() != null && qr.getMesa().getUnidadeAtendimento() != null) return qr.getMesa().getUnidadeAtendimento();
        throw new BusinessException("QR não possui unidade de atendimento configurada.");
    }

    private List<Produto> validarECarregarProdutosDoTenant(Long tenantId, List<PublicQrPedidoItemRequest> itens) {
        List<Produto> produtos = new ArrayList<>();
        for (PublicQrPedidoItemRequest item : itens) {
            Produto produto = produtoRepository.findByIdAndTenantId(item.getProdutoId(), tenantId)
                    .orElseThrow(() -> new BusinessException("Produto inválido ou indisponível."));
            if (!Boolean.TRUE.equals(produto.getAtivo()) || !Boolean.TRUE.equals(produto.getDisponivel())) {
                throw new BusinessException("Produto inválido ou indisponível.");
            }
            produtos.add(produto);
        }
        return produtos;
    }

    private Map<Cozinha, List<PublicQrPedidoItemRequest>> agruparItensPorCozinha(
            Long unidadeAtendimentoId,
            List<PublicQrPedidoItemRequest> itens,
            List<Produto> produtos
    ) {
        Map<Cozinha, List<PublicQrPedidoItemRequest>> requestsPorCozinha = new HashMap<>();
        for (PublicQrPedidoItemRequest item : itens) {
            Produto produto = produtos.stream()
                    .filter(p -> p.getId().equals(item.getProdutoId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Produto inválido ou indisponível."));

            Cozinha cozinha = subPedidoService.determinarCozinha(produto, unidadeAtendimentoId);
            if (!Boolean.TRUE.equals(cozinha.getAtiva())) {
                throw new BusinessException("Produto inválido ou indisponível.");
            }
            requestsPorCozinha.computeIfAbsent(cozinha, k -> new ArrayList<>()).add(item);
        }
        return requestsPorCozinha;
    }

    private SessaoConsumo resolverOuCriarSessaoMinima(
            QrCodeOperacional qr,
            Instituicao instituicao,
            UnidadeAtendimento unidadeAtendimento,
            Mesa mesa
    ) {
        if (qr.getTipo() == QrCodeOperacionalTipo.MESA && mesa != null) {
            return sessaoConsumoRepository.findByMesaIdAndStatus(mesa.getId(), StatusSessaoConsumo.ABERTA)
                    .filter(s -> s.getInstituicao() != null && s.getInstituicao().getId().equals(instituicao.getId()))
                    .orElseGet(() -> criarSessaoMinima(instituicao, unidadeAtendimento, mesa));
        }
        return criarSessaoMinima(instituicao, unidadeAtendimento, mesa);
    }

    private SessaoConsumo criarSessaoMinima(Instituicao instituicao, UnidadeAtendimento unidadeAtendimento, Mesa mesa) {
        SessaoConsumo sessao = SessaoConsumo.builder()
                .qrCodeSessao(UUID.randomUUID().toString())
                .instituicao(instituicao)
                .unidadeAtendimento(unidadeAtendimento)
                .mesa(mesa)
                .modoAnonimo(true)
                .status(StatusSessaoConsumo.ABERTA)
                .tipoSessao(TipoSessao.POS_PAGO)
                .build();
        SessaoConsumo salva = sessaoConsumoRepository.save(sessao);
        fundoConsumoService.criarFundoParaSessao(salva);
        return salva;
    }

    private String gerarNumeroPedido() {
        String dataAtual = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = pedidoRepository.count() + 1;
        return String.format("PED-%s-%03d", dataAtual, count);
    }
}
