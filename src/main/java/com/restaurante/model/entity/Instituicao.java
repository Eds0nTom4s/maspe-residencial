package com.restaurante.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidade Instituição (Tenant)
 * 
 * Representa a base do sistema multi-tenant. Cada instituição (ex: MASPE, Arena, Restaurante X)
 * possui os seus próprios dados isolados no futuro.
 * 
 * Propósito atual: Fornecer dinamicamente propriedades de branding (como a Sigla)
 * para geração de Tokens, QR Codes e Personalização visual (Logo).
 */
@Entity
@Table(name = "instituicoes", indexes = {
    @Index(name = "idx_instituicao_ativa", columnList = "ativa"),
    @Index(name = "idx_instituicao_sigla", columnList = "sigla", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Instituicao extends BaseEntity {

    @NotBlank(message = "O nome da instituição é obrigatório")
    @Column(nullable = false, length = 150)
    private String nome;

    @NotBlank(message = "A sigla da instituição é obrigatória")
    @Column(nullable = false, length = 10, unique = true)
    private String sigla;

    @NotBlank(message = "O NIF da instituição é obrigatório")
    @Column(nullable = false, length = 50, unique = true)
    private String nif;

    @Column(name = "url_logo", length = 500)
    private String urlLogo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativa = true;

    @NotBlank(message = "O telefone de autorização é obrigatório")
    @Column(name = "telefone_autorizacao", nullable = false, length = 20)
    private String telefoneAutorizacao;

    @Column(name = "otp_code", length = 10)
    private String otpCode;

    @Column(name = "otp_expiration")
    private LocalDateTime otpExpiration;

    public boolean isOtpValido(String codigo) {
        if (this.otpCode == null || this.otpExpiration == null) {
            return false;
        }
        if (LocalDateTime.now().isAfter(this.otpExpiration)) {
            return false;
        }
        return this.otpCode.equals(codigo);
    }

}
