package com.restaurante.dto.response;

import java.time.LocalDateTime;

public class PlatformCardapioLimitsResponse {

    private Long tenantId;
    private Integer maxCategorias;
    private Integer maxProdutos;
    private LocalDateTime alteradoEm;
    private String alteradoPor;
    private String motivo;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Integer getMaxCategorias() { return maxCategorias; }
    public void setMaxCategorias(Integer maxCategorias) { this.maxCategorias = maxCategorias; }
    public Integer getMaxProdutos() { return maxProdutos; }
    public void setMaxProdutos(Integer maxProdutos) { this.maxProdutos = maxProdutos; }
    public LocalDateTime getAlteradoEm() { return alteradoEm; }
    public void setAlteradoEm(LocalDateTime alteradoEm) { this.alteradoEm = alteradoEm; }
    public String getAlteradoPor() { return alteradoPor; }
    public void setAlteradoPor(String alteradoPor) { this.alteradoPor = alteradoPor; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
