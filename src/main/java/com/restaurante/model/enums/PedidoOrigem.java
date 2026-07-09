package com.restaurante.model.enums;

/**
 * Origem operacional concreta de um pedido.
 *
 * <p>Diferente do template operacional do tenant (ex: CONSUMA_REST_V1),
 * este enum representa o canal/pelo qual o pedido nasceu no sistema.
 *
 * <p>Regras de governança:
 * <ul>
 *   <li>QR_MESA / QR_PRINCIPAL / QR_PUBLICO: pedidos públicos via QR — exigem aceite antes do pagamento.</li>
 *   <li>SESSAO_PARTICIPANTE: pedido atribuído a um participante de sessão compartilhada.</li>
 *   <li>SESSAO_CONSUMO: pedido originado em sessão de consumo identificada (cliente/app).</li>
 *   <li>OPERADOR_INTERNO: pedido criado por um operador do tenant (admin/owner/operator/caixa).</li>
 *   <li>DEVICE_POS: pedido criado por um dispositivo POS.</li>
 *   <li>PDV_INTERNO: pedido criado no PDV interno (fluxo futuro, mapeado para regras de pagamento imediato).</li>
 *   <li>DEVICE_KDS: pedido criado ou movido por KDS (não confirma pagamento).</li>
 *   <li>CAIXA: pedido caixa (não envia para produção).</li>
 *   <li>SISTEMA: pedido criado por job/worker/automação.</li>
 *   <li>LEGADO: pedidos anteriores à formalização da origem; fallback conservador.</li>
 *   <li>UNKNOWN: quando não é possível determinar a origem.</li>
 * </ul>
 */
public enum PedidoOrigem {
    QR_MESA,
    QR_PRINCIPAL,
    QR_PUBLICO,
    SESSAO_PARTICIPANTE,
    SESSAO_CONSUMO,
    OPERADOR_INTERNO,
    DEVICE_POS,
    PDV_INTERNO,
    DEVICE_KDS,
    CAIXA,
    SISTEMA,
    LEGADO,
    UNKNOWN
}
