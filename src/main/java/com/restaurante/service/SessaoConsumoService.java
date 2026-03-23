package com.restaurante.service;

import com.restaurante.dto.request.AbrirSessaoRequest;
import com.restaurante.dto.response.SessaoConsumoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.repository.AtendenteRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.restaurante.notificacao.service.NotificacaoService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;

/**
 * Service responsável pelo ciclo de vida da SessaoConsumo.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Abrir nova sessão (com ou sem mesa).</li>
 *   <li>Criar automaticamente o FundoConsumo ao abrir sessão.</li>
 *   <li>Gerar qrCodeSessao único para cada sessão.</li>
 *   <li>Encerrar sessão.</li>
 *   <li>Sinalizar sessão para aguardar pagamento.</li>
 *   <li>Consultar histórico de sessões por mesa.</li>
 * </ul>
 *
 * <p>Regras fundamentais:
 * <ul>
 *   <li>Mesa é OPCIONAL — sessão pode existir sem mesa.</li>
 *   <li>Quando mesa presente: apenas UMA sessão ABERTA por mesa.</li>
 *   <li>Cada sessão recebe um FundoConsumo com saldo zero automaticamente.</li>
 *   <li>O qrCodeSessao é a identidade pública da sessão e do seu fundo.</li>
 * </ul>
 */
@Service
public class SessaoConsumoService {

    private static final Logger log = LoggerFactory.getLogger(SessaoConsumoService.class);

    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final MesaRepository mesaRepository;
    private final ClienteService clienteService;
    private final AtendenteRepository atendenteRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final FundoConsumoService fundoConsumoService;
    private final PedidoFinanceiroService pedidoFinanceiroService;

    private final PedidoRepository pedidoRepository;
    private final QrCodeService qrCodeService;
    private final NotificacaoService notificacaoService;

    public SessaoConsumoService(SessaoConsumoRepository sessaoConsumoRepository,
                                MesaRepository mesaRepository,
                                ClienteService clienteService,
                                AtendenteRepository atendenteRepository,
                                UnidadeAtendimentoRepository unidadeAtendimentoRepository,
                                FundoConsumoService fundoConsumoService,
                                PedidoFinanceiroService pedidoFinanceiroService,
                                PedidoRepository pedidoRepository,
                                QrCodeService qrCodeService,
                                NotificacaoService notificacaoService) {
        this.sessaoConsumoRepository = sessaoConsumoRepository;
        this.mesaRepository = mesaRepository;
        this.clienteService = clienteService;
        this.atendenteRepository = atendenteRepository;
        this.unidadeAtendimentoRepository = unidadeAtendimentoRepository;
        this.fundoConsumoService = fundoConsumoService;
        this.pedidoFinanceiroService = pedidoFinanceiroService;
        this.pedidoRepository = pedidoRepository;
        this.qrCodeService = qrCodeService;
        this.notificacaoService = notificacaoService;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Operações de ciclo de vida
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Abre uma nova sessão de consumo.
     *
     * <p>Mesa é opcional. Quando fornecida, valida unicidade de sessão aberta.
     * Ao abrir, cria automaticamente o FundoConsumo com saldo zero.
     * Um qrCodeSessao único é gerado — identidade da sessão e chave de acesso ao fundo.
     */
    @Transactional
    public SessaoConsumoResponse abrir(AbrirSessaoRequest request) {
        log.info("Abrindo sessão de consumo: mesaId={}, modoAnonimo={}, unidadeAtendimentoId={}",
                request.getMesaId(), request.isModoAnonimo(), request.getUnidadeAtendimentoId());

        SessaoConsumo.SessaoConsumoBuilder builder = SessaoConsumo.builder()
                .modoAnonimo(request.isModoAnonimo());
                
        // ── Tipo de Sessão ────────────────────────────────────────────────
        // Se não for especificado, assume que é para continuar com o padrão PRE_PAGO 
        // ou você pode definir na classe SessaoConsumo como default. 
        // Estamos garantindo que venha do request. Se nulo, default=PRE_PAGO
        com.restaurante.model.enums.TipoSessao tipoSessao = request.getTipoSessao() != null ? 
                        request.getTipoSessao() : com.restaurante.model.enums.TipoSessao.PRE_PAGO;
        builder.tipoSessao(tipoSessao);

        // ── Mesa (OPCIONAL) ────────────────────────────────────────────────
        if (request.getMesaId() != null) {
            Mesa mesa = mesaRepository.findById(request.getMesaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada: " + request.getMesaId()));

            if (!mesa.getAtiva()) {
                throw new BusinessException("Mesa '" + mesa.getReferencia() + "' está inativa");
            }

            // Invariante: apenas UMA sessão ABERTA por mesa
            if (sessaoConsumoRepository.existsByMesaIdAndStatus(mesa.getId(), StatusSessaoConsumo.ABERTA)) {
                throw new BusinessException(
                        "Mesa '" + mesa.getReferencia() + "' já possui sessão aberta. " +
                        "Encerre a sessão atual antes de abrir uma nova.");
            }

            builder.mesa(mesa);

            // Deriva unidade de atendimento da mesa quando não fornecida explicitamente
            if (request.getUnidadeAtendimentoId() == null && mesa.getUnidadeAtendimento() != null) {
                builder.unidadeAtendimento(mesa.getUnidadeAtendimento());
            }

            log.info("Sessão associada à mesa: '{}' (ID={})", mesa.getReferencia(), mesa.getId());
        }

        // ── Unidade de Atendimento explícita ──────────────────────────────
        if (request.getUnidadeAtendimentoId() != null) {
            UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(request.getUnidadeAtendimentoId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Unidade de atendimento não encontrada: " + request.getUnidadeAtendimentoId()));
            builder.unidadeAtendimento(ua);
        }

        // ── Cliente (OPCIONAL) ─────────────────────────────────────────────
        if (!request.isModoAnonimo()) {
            if (request.getTelefoneCliente() == null || request.getTelefoneCliente().isBlank()) {
                throw new BusinessException("Telefone do cliente é obrigatório no fluxo identificado");
            }
            Cliente cliente = clienteService.buscarOuCriarPorTelefone(request.getTelefoneCliente(), request.getNomeCliente());

            // Impede cliente com sessão aberta (qualquer mesa)
            sessaoConsumoRepository.findSessaoAbertaByCliente(cliente.getId())
                    .ifPresent(sessaoExistente -> {
                        String localSessao = sessaoExistente.getMesa() != null
                                ? "mesa '" + sessaoExistente.getMesa().getReferencia() + "'"
                                : "sessão #" + sessaoExistente.getId();
                                
                        String nomeExibicao = (cliente.getNome() != null && !cliente.getNome().isBlank()) 
                                ? cliente.getNome() 
                                : cliente.getTelefone();

                        throw new BusinessException(
                                "Cliente '" + nomeExibicao + "' já possui sessão aberta em " + localSessao);
                    });

            builder.cliente(cliente);
            log.info("Sessão identificada: clienteId={}", cliente.getId());
        } else {
            log.info("Sessão no modo anónimo — portador identificado pelo qrCodeSessao");
        }

        // ── Atendente ──────────────────────────────────────────────────────
        if (request.getAtendenteId() != null) {
            Atendente atendente = atendenteRepository.findById(request.getAtendenteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Atendente não encontrado: " + request.getAtendenteId()));
            builder.aberturaPor(atendente);
        }

        // Persiste a sessão (qrCodeSessao já gerado pelo @Builder.Default)
        SessaoConsumo sessao = builder.build();
        SessaoConsumo sessaoSalva = sessaoConsumoRepository.save(sessao);

        // Cria automaticamente o FundoConsumo com saldo zero
        FundoConsumo fundo = fundoConsumoService.criarFundoParaSessao(sessaoSalva);

        log.info("Sessão aberta: ID={}, QR={}, fundo=ID:{}, status={}",
                sessaoSalva.getId(), sessaoSalva.getQrCodeSessao(), fundo.getId(), sessaoSalva.getStatus());

        // Enviar notificação SMS para o cliente
        if (!request.isModoAnonimo() && request.getTelefoneCliente() != null && !request.getTelefoneCliente().isBlank()) {
            final String referenciaMesa = (sessaoSalva.getMesa() != null) ? sessaoSalva.getMesa().getReferencia() : null;
            try {
                notificacaoService.enviarNotificacaoSessaoCriada(
                    request.getTelefoneCliente(),
                    referenciaMesa,
                    sessaoSalva.getQrCodeSessao()
                );
            } catch (Exception e) {
                log.error("Erro ao enviar SMS de abertura de sessão: {}", e.getMessage());
            }
        }

        return converterParaResponse(sessaoSalva, fundo);
    }

    /**
     * Encerra a sessão de consumo e o seu fundo.
     *
     * IM-4: Verifica se existem pedidos activos (CRIADO, EM_ANDAMENTO) antes de encerrar.
     */
    @Transactional
    public SessaoConsumoResponse fechar(Long id) {
        log.info("Encerrando sessão de consumo ID={}", id);

        SessaoConsumo sessao = buscarEntidadePorId(id);

        if (sessao.getStatus() == StatusSessaoConsumo.ENCERRADA) {
            throw new BusinessException("Sessão ID=" + id + " já está encerrada");
        }

        // IM-4: Impede encerramento com pedidos em aberto
        var pedidosAbertos = pedidoRepository.findBySessaoConsumoIdAndStatusInOrderByCreatedAtAsc(
                id, java.util.List.of(StatusPedido.CRIADO, StatusPedido.EM_ANDAMENTO));
        if (!pedidosAbertos.isEmpty()) {
            throw new BusinessException(
                String.format("Não é possível encerrar a sessão: existem %d pedido(s) em aberto. " +
                              "Conclua ou cancele todos os pedidos antes de encerrar.",
                              pedidosAbertos.size()));
        }
        
        // Bloqueio POS_PAGO: Impede encerramento caso existam pedidos NAO_PAGO
        if (sessao.getTipoSessao() == com.restaurante.model.enums.TipoSessao.POS_PAGO) {
            boolean temPedidoNaoPago = sessao.getPedidos().stream()
                .anyMatch(p -> p.getStatusFinanceiro() == StatusFinanceiroPedido.NAO_PAGO);
                
            if (temPedidoNaoPago) {
                throw new BusinessException(
                    "Sessão POS_PAGO possui fatura pendente. Execute o checkout (liquidarContaPosPago) antes de encerrar."
                );
            }
        }

        sessao.encerrar();  // encerra sessão e fundo (ver método na entidade)
        SessaoConsumo sessaoSalva = sessaoConsumoRepository.save(sessao);

        String local = sessao.getMesa() != null ? "mesa '" + sessao.getMesa().getReferencia() + "'" : "sessão #" + id;
        log.info("Sessão encerrada: ID={}, {}", id, local);

        return converterParaResponse(sessaoSalva, sessaoSalva.getFundoConsumo());
    }

    /**
     * Transiciona sessão para AGUARDANDO_PAGAMENTO.
     */
    @Transactional
    public SessaoConsumoResponse aguardarPagamento(Long id) {
        log.info("Transicionando sessão ID={} para AGUARDANDO_PAGAMENTO", id);

        SessaoConsumo sessao = buscarEntidadePorId(id);

        if (sessao.getStatus() != StatusSessaoConsumo.ABERTA) {
            throw new BusinessException(
                    "Sessão deve estar ABERTA para aguardar pagamento. Status atual: " + sessao.getStatus());
        }

        sessao.aguardarPagamento();
        SessaoConsumo sessaoSalva = sessaoConsumoRepository.save(sessao);
        return converterParaResponse(sessaoSalva, sessaoSalva.getFundoConsumo());
    }

    /**
     * Checkout e Liquidação de Sessão POS_PAGO.
     * Centraliza a confirmação de pagamento de todos os pedidos pendentes
     * e transiciona a sessão de AGUARDANDO_PAGAMENTO para ENCERRADA.
     */
    @Transactional
    public SessaoConsumoResponse liquidarContaPosPago(Long id, String metodoPagamento, String qrCodeFundoExterno, String telefone) {
        log.info("Liquidando conta POS_PAGO da sessão ID={} com método {}", id, metodoPagamento);
        
        SessaoConsumo sessao = buscarEntidadePorId(id);
        
        if (sessao.getTipoSessao() != com.restaurante.model.enums.TipoSessao.POS_PAGO) {
            throw new BusinessException("Operação permitida apenas para sessões POS_PAGO");
        }
        
        if (sessao.getStatus() == StatusSessaoConsumo.ENCERRADA) {
            throw new BusinessException("Sessão já está encerrada.");
        }
        
        // Verifica se há pedidos em aberto (não finalizados)
        var pedidosAbertos = pedidoRepository.findBySessaoConsumoIdAndStatusInOrderByCreatedAtAsc(
                id, java.util.List.of(StatusPedido.CRIADO, StatusPedido.EM_ANDAMENTO));
        if (!pedidosAbertos.isEmpty()) {
            throw new BusinessException(
                "Não é possível liquidar a conta: existem pedidos ainda em produção/abertos. " +
                "Feche ou cancele todos os pedidos primeiro."
            );
        }

        // Marcar sessão como aguardando pagamento
        if (sessao.getStatus() == StatusSessaoConsumo.ABERTA) {
            sessao.aguardarPagamento();
            sessaoConsumoRepository.save(sessao);
        }

        // Processa todos os pedidos NAO_PAGO
        List<Pedido> pedidosPendentes = sessao.getPedidos().stream()
                .filter(p -> p.getStatusFinanceiro() == StatusFinanceiroPedido.NAO_PAGO)
                .collect(Collectors.toList());
                
        int countPagas = 0;
        BigDecimal valorTotalLiquidado = BigDecimal.ZERO;
        
        // Determina se pagamento via Fundo (externo ou o da sessão)
        boolean pagarComFundo = "FUNDO_CONSUMO".equalsIgnoreCase(metodoPagamento);
        String tokenFundoParaPagamento = qrCodeFundoExterno;
        if (pagarComFundo && (tokenFundoParaPagamento == null || tokenFundoParaPagamento.isBlank())) {
            // Se deseja pagar com fundo, mas não passou um externo, usamos o da própria sessão, se post-pago o permitiria 
            // Porém, POS_PAGO não tem dinheiro na sessão (a menos que tenham recarregado como poupança).
            if (sessao.getFundoConsumo() != null && sessao.getQrCodeSessao() != null) {
                 tokenFundoParaPagamento = sessao.getQrCodeSessao();
            } else {
                 throw new BusinessException("QR Code do Fundo de Consumo não informado para o pagamento");
            }
        }
        
        for (Pedido pedido : pedidosPendentes) {
            if (pagarComFundo) {
                // Modifica o tipo de pagamento para pré-pago a meio do voo para o financeiro service aceitar o débito
                // ou apenas chama processarPagamento normal (assumindo que já validou total do fundo)
                
                // Pedido foi POS_PAGO, entao ele tem que se tornar PRE_PAGO ou o service aceitar pagar o post-pago com o fundo.
                // Como PedidoFinanceiroService tem um processamento directo, vamos debitar do fundo.
                try {
                    FundoConsumo fundo = fundoConsumoService.buscarPorToken(tokenFundoParaPagamento);
                    fundoConsumoService.debitarDireto(fundo, pedido.getId(), pedido.getTotal() != null ? pedido.getTotal() : BigDecimal.ZERO);
                    pedido.marcarComoPago();
                    pedidoRepository.save(pedido);
                    countPagas++;
                    valorTotalLiquidado = valorTotalLiquidado.add(pedido.getTotal() != null ? pedido.getTotal() : BigDecimal.ZERO);
                } catch (Exception e) {
                    throw new BusinessException("Erro ao pagar com fundo: " + e.getMessage());
                }
            } else {
                // Cash, TPA, AppyPay (registrado externamente)
                pedidoFinanceiroService.confirmarPagamentoPosPago(pedido.getId());
                countPagas++;
                valorTotalLiquidado = valorTotalLiquidado.add(pedido.getTotal() != null ? pedido.getTotal() : BigDecimal.ZERO);
            }
        }
        
        log.info("Liquidação POS_PAGO concluída: {} faturas pagas (Método: {}), Total: {}", 
                 countPagas, metodoPagamento, com.restaurante.util.MoneyFormatter.format(valorTotalLiquidado));
        
        // Retorna a view atualizada da sessão (após isso, pode-se fechar com sucesso)
        return fechar(id);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Consultas
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SessaoConsumoResponse buscarPorId(Long id) {
        SessaoConsumo sessao = buscarEntidadePorId(id);
        return converterParaResponse(sessao, sessao.getFundoConsumo());
    }

    @Transactional(readOnly = true)
    public SessaoConsumoResponse buscarSessaoAbertaDaMesa(Long mesaId) {
        SessaoConsumo sessao = sessaoConsumoRepository
                .findByMesaIdAndStatus(mesaId, StatusSessaoConsumo.ABERTA)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nenhuma sessão ABERTA encontrada para a mesa ID=" + mesaId));
        return converterParaResponse(sessao, sessao.getFundoConsumo());
    }

    @Transactional(readOnly = true)
    public List<SessaoConsumoResponse> listarAbertas() {
        return sessaoConsumoRepository.findByStatus(StatusSessaoConsumo.ABERTA).stream()
                .map(s -> converterParaResponse(s, s.getFundoConsumo()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SessaoConsumoResponse> listarPorMesa(Long mesaId) {
        return sessaoConsumoRepository.findByMesaIdOrderByAbertaEmDesc(mesaId).stream()
                .map(s -> converterParaResponse(s, s.getFundoConsumo()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SessaoConsumoResponse> listarPorStatus(StatusSessaoConsumo status) {
        return sessaoConsumoRepository.findByStatus(status).stream()
                .map(s -> converterParaResponse(s, s.getFundoConsumo()))
                .collect(Collectors.toList());
    }


    // ──────────────────────────────────────────────────────────────────────────
    // Operações para o Cliente (QR Ordering)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Cliente entra na sessão via Token do QR Code da mesa.
     * Se a mesa não possui sessão aberta, abre uma nova para o cliente.
     * Se já possui sessão aberta e anônima, associa o cliente a ela.
     */
    @Transactional
    public SessaoConsumoResponse iniciarSessaoCliente(String qrToken, String telefoneCliente) {
        log.info("Cliente {} iniciando sessão via QR Code {}", telefoneCliente, qrToken);

        // Valida o QR Code
        var validacao = qrCodeService.validarQrCode(qrToken);
        if (!validacao.getValido()) {
            throw new BusinessException("QR Code inválido ou expirado: " + validacao.getMensagem());
        }

        var qrCode = validacao.getQrCode();
        if (qrCode.getTipo() != com.restaurante.model.enums.TipoQrCode.MESA || qrCode.getMesaId() == null) {
            throw new BusinessException("QR Code não é de uma mesa válida");
        }

        Cliente cliente = clienteService.buscarOuCriarPorTelefone(telefoneCliente);

        // Verifica se cliente já tem sessão aberta noutro lugar
        sessaoConsumoRepository.findSessaoAbertaByCliente(cliente.getId())
                .ifPresent(sessaoExistente -> {
                    if (!sessaoExistente.getMesa().getId().equals(qrCode.getMesaId())) {
                        throw new BusinessException(
                                "Você já possui uma sessão aberta na mesa '" + sessaoExistente.getMesa().getReferencia() + "'. Feche-a primeiro.");
                    }
                });

        // Verifica se a mesa atual já tem sessão aberta
        var sessaoOpt = sessaoConsumoRepository.findByMesaIdAndStatus(qrCode.getMesaId(), StatusSessaoConsumo.ABERTA);

        SessaoConsumo sessao;
        if (sessaoOpt.isPresent()) {
            sessao = sessaoOpt.get();
            // Se já tem sessão, entra nela. Se era anônima, associa ao cliente.
            if (sessao.getCliente() == null) {
                sessao.setCliente(cliente);
                sessao.setModoAnonimo(false);
                sessao = sessaoConsumoRepository.save(sessao);
                
                String nomeIdentificacao = (cliente.getNome() != null && !cliente.getNome().isBlank()) 
                        ? cliente.getNome() 
                        : cliente.getTelefone();
                log.info("Cliente {} associou-se à Sessão ID={}", nomeIdentificacao, sessao.getId());
            } else if (!sessao.getCliente().getId().equals(cliente.getId())) {
                String nomeAtual = (sessao.getCliente().getNome() != null && !sessao.getCliente().getNome().isBlank())
                        ? sessao.getCliente().getNome()
                        : sessao.getCliente().getTelefone();
                String nomeConvidado = (cliente.getNome() != null && !cliente.getNome().isBlank())
                        ? cliente.getNome()
                        : cliente.getTelefone();
                        
                log.info("Sessão da mesa já pertence ao cliente {}, adicionando {} como convidado (funcionalidade futura, por agora retornamos sucesso)", 
                    nomeAtual, nomeConvidado);
                // Futuro: Lógica de convidados. Por agora, apenas permitimos a visualização.
            }
        } else {
            // Abre nova sessão para o cliente
            AbrirSessaoRequest req = AbrirSessaoRequest.builder()
                    .mesaId(qrCode.getMesaId())
                    .modoAnonimo(false)
                    .telefoneCliente(telefoneCliente)
                    // No fluxo de QR code, por enquanto não temos campo de nome no parâmetro
                    // Mas a carcaça do DTO já suporta. Passamos null.
                    .nomeCliente(null) 
                    .tipoSessao(com.restaurante.model.enums.TipoSessao.PRE_PAGO)
                    .build();
            return abrir(req);
        }

        return converterParaResponse(sessao);
    }

    /**
     * Retorna a sessão ativa do Cliente logado
     */
    @Transactional(readOnly = true)
    public SessaoConsumoResponse buscarMinhaSessao(String telefoneCliente) {
        Cliente cliente = clienteService.buscarPorTelefone(telefoneCliente);
        
        SessaoConsumo sessao = sessaoConsumoRepository.findSessaoAbertaByCliente(cliente.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Você não possui nenhuma sessão ativa no momento."));
                
        return converterParaResponse(sessao);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Consultas Admin
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Lista sessões com filtros avançados e paginação.
     */
    @Transactional(readOnly = true)
    public Page<SessaoConsumoResponse> listarComFiltros(
            StatusSessaoConsumo status,
            LocalDateTime dataInicio,
            LocalDateTime dataFim,
            Pageable pageable) {
        
        log.info("Listando sessões com filtros — status={}, inicio={}, fim={}", status, dataInicio, dataFim);
        
        Specification<SessaoConsumo> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            
            if (dataInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("abertaEm"), dataInicio));
            }
            
            if (dataFim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("abertaEm"), dataFim));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        
        return sessaoConsumoRepository.findAll(spec, pageable)
                .map(this::converterParaResponse);
    }

    /**
     * Lista todas as sessões (sem filtro) — uso admin.
     * @deprecated Use listarComFiltros
     */
    @Transactional(readOnly = true)
    @Deprecated
    public List<SessaoConsumoResponse> listarTodas() {
        log.info("Listando todas as sessões (admin)");
        return sessaoConsumoRepository.findAll().stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers internos
    // ──────────────────────────────────────────────────────────────────────────

    public SessaoConsumo buscarEntidadePorId(Long id) {
        return sessaoConsumoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão de consumo não encontrada: " + id));
    }

    public SessaoConsumoResponse converterParaResponse(SessaoConsumo sessao) {
        return converterParaResponse(sessao, sessao.getFundoConsumo());
    }

    public SessaoConsumoResponse converterParaResponse(SessaoConsumo sessao, FundoConsumo fundo) {
        BigDecimal totalConsumo = sessao.getPedidos().stream()
                .map(p -> p.getTotal() != null ? p.getTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SessaoConsumoResponse.builder()
                .id(sessao.getId())
                .qrCodeSessao(sessao.getQrCodeSessao())
                .mesaId(sessao.getMesa() != null ? sessao.getMesa().getId() : null)
                .referenciaMesa(sessao.getMesa() != null ? sessao.getMesa().getReferencia() : null)
                .clienteId(sessao.getCliente() != null ? sessao.getCliente().getId() : null)
                .nomeCliente(sessao.getCliente() != null ? sessao.getCliente().getNome() : null)
                .telefoneCliente(sessao.getCliente() != null ? sessao.getCliente().getTelefone() : null)
                .aberturaPorId(sessao.getAberturaPor() != null ? sessao.getAberturaPor().getId() : null)
                .nomeAtendente(sessao.getAberturaPor() != null ? sessao.getAberturaPor().getNome() : null)
                .abertaEm(sessao.getAbertaEm())
                .fechadaEm(sessao.getFechadaEm())
                .status(sessao.getStatus())
                .tipoSessao(sessao.getTipoSessao())
                .modoAnonimo(sessao.getModoAnonimo())
                .fundoId(fundo != null ? fundo.getId() : null)
                .saldoFundo(fundo != null ? fundo.getSaldoAtual() : BigDecimal.ZERO)
                .totalConsumo(totalConsumo)
                .build();
    }
}
