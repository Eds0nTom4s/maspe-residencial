package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformPlanResponse {

    private Long id;
    private String codigo;
    private String nome;
    private String descricao;
    private String moeda;
    private String ciclo;
    private BigDecimal precoMensal;
    private Boolean ativo;
    private Integer maxInstituicoes;
    private Integer maxUnidadesAtendimento;
    private Integer maxProdutos;
    private Integer maxCategorias;
    private Integer maxUsuarios;
    private Integer maxQrCodes;
    private Integer maxDispositivos;
    private Boolean permiteMultiInstituicao;
    private Boolean permitePedidosQr;
    private Boolean permitePos;
    private Boolean permiteOffline;
}
