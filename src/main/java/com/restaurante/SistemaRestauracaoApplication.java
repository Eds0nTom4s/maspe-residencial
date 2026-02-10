package com.restaurante;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe principal da aplicação
 * Sistema de Gestão Inteligente de Mesas, Pedidos e Pagamentos por QR Code
 * 
 * Auditoria JPA configurada em: @see com.restaurante.config.AuditorConfig
 * Scheduling habilitado para jobs automáticos (expiração de QR Codes, etc)
 * 
 * @author Equipe de Desenvolvimento
 * @version 1.0.0
 */
@SpringBootApplication
@EnableScheduling
public class SistemaRestauracaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SistemaRestauracaoApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("Sistema de Restauração iniciado com sucesso!");
        System.out.println("Documentação da API: http://localhost:8080/api/swagger-ui.html");
        System.out.println("========================================\n");
    }
}
