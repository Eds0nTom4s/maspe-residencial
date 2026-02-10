package com.restaurante.model.entity;

import com.restaurante.model.enums.TipoUsuario;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Entidade Cliente
 * Representa um cliente que acessa o sistema via QR Code
 * Cliente é identificado pelo número de telefone e autenticado via OTP
 */
@Entity
@Table(name = "clientes", indexes = {
    @Index(name = "idx_cliente_telefone", columnList = "telefone", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente extends BaseEntity {

    @NotBlank(message = "Telefone é obrigatório")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Formato de telefone inválido")
    @Column(nullable = false, unique = true, length = 20)
    private String telefone;

    @Size(max = 100)
    @Column(length = 100)
    private String nome;

    @Column(name = "telefone_verificado", nullable = false)
    @Builder.Default
    private Boolean telefoneVerificado = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false)
    @Builder.Default
    private TipoUsuario tipoUsuario = TipoUsuario.CLIENTE;

    @Column(name = "otp_code", length = 10)
    private String otpCode;

    @Column(name = "otp_expiration")
    private java.time.LocalDateTime otpExpiration;

    @Column(name = "ativo", nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    // Relacionamento com unidades de consumo
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<UnidadeDeConsumo> unidadesConsumo = new ArrayList<>();

    /**
     * Verifica se o OTP ainda é válido
     */
    public boolean isOtpValido(String otp) {
        return this.otpCode != null 
            && this.otpCode.equals(otp) 
            && this.otpExpiration != null 
            && this.otpExpiration.isAfter(java.time.LocalDateTime.now());
    }
}
