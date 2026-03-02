package com.restaurante.config;

import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

/**
 * DataSeeder para inicialização de dados de desenvolvimento
 * 
 * RESPONSABILIDADES:
 * - Criar usuário admin padrão
 * - Criar cozinhas de exemplo
 * - Criar produtos de exemplo
 * - Criar unidades de atendimento e consumo
 * 
 * REGRAS:
 * - Executado APENAS em perfil 'dev'
 * - Executado APÓS InicializacaoFinanceiraConfig (Order 100)
 * - Verifica se dados já existem antes de criar (idempotente)
 * - Não sobrescreve dados existentes
 */
@Component
@Profile("dev")
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

    private final UserRepository userRepository;
    private final AtendenteRepository atendenteRepository;
    private final CozinhaRepository cozinhaRepository;
    private final ProdutoRepository produtoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final UnidadeDeConsumoRepository unidadeDeConsumoRepository;
    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        log.info("=".repeat(80));
        log.info("🌱 INICIANDO DATA SEEDER (Perfil: dev)");
        log.info("=".repeat(80));

        seedUsuarios();
        seedAtendentes();
        seedClientes();
        seedUnidadesAtendimento();
        seedCozinhas();
        seedProdutos();
        seedUnidadesConsumo();

        log.info("=".repeat(80));
        log.info("✅ DATA SEEDER CONCLUÍDO COM SUCESSO");
        log.info("=".repeat(80));
    }

    private void seedUsuarios() {
        log.info("👥 Verificando usuários...");

        if (userRepository.count() > 0) {
            log.info("  ┗ Usuários já existem. Pulando...");
            return;
        }

        log.info("🔐 PasswordEncoder sendo usado: {}", passwordEncoder.getClass().getSimpleName());

        // 1. Usuário ADMIN
        String adminPasswordPlain = "admin123";
        String adminPasswordEncoded = passwordEncoder.encode(adminPasswordPlain);
        log.info("🔐 DEBUG Admin - Senha Plain: {}, Senha Encoded: {}", adminPasswordPlain, adminPasswordEncoded);
        
        User admin = User.builder()
            .username("admin")
            .password(adminPasswordEncoded)
            .nomeCompleto("Administrador do Sistema")
            .telefone("+244923456789")
            .email("admin@restaurante.ao")
            .roles(Set.of(Role.ROLE_ADMIN))
            .ativo(true)
            .build();
        userRepository.save(admin);
        
        // Teste de verificação
        boolean matchTest = passwordEncoder.matches(adminPasswordPlain, adminPasswordEncoded);
        log.info("  ┣ ✅ Admin criado: username=admin, senha=admin123");
        log.info("  ┣ 🔍 Teste de match: passwordEncoder.matches('admin123', hash) = {}", matchTest);

        // 2. Usuário ATENDENTE
        User atendente = User.builder()
            .username("atendente")
            .password(passwordEncoder.encode("atendente123"))
            .nomeCompleto("João Silva - Atendente")
            .telefone("+244923111222")
            .email("atendente@restaurante.ao")
            .roles(Set.of(Role.ROLE_ATENDENTE))
            .ativo(true)
            .build();
        userRepository.save(atendente);
        log.info("  ┣ ✅ Atendente criado: username=atendente, senha=atendente123");

        // 3. Usuário COZINHA
        User cozinha = User.builder()
            .username("cozinha")
            .password(passwordEncoder.encode("cozinha123"))
            .nomeCompleto("Maria Santos - Cozinha")
            .telefone("+244923222333")
            .email("cozinha@restaurante.ao")
            .roles(Set.of(Role.ROLE_COZINHA))
            .ativo(true)
            .build();
        userRepository.save(cozinha);
        log.info("  ┣ ✅ Cozinha criado: username=cozinha, senha=cozinha123");

        // 4. Usuário GERENTE
        User gerente = User.builder()
            .username("gerente")
            .password(passwordEncoder.encode("gerente123"))
            .nomeCompleto("Carlos Mendes - Gerente")
            .telefone("+244923333444")
            .email("gerente@restaurante.ao")
            .roles(Set.of(Role.ROLE_GERENTE))
            .ativo(true)
            .build();
        userRepository.save(gerente);
        log.info("  ┗ ✅ Gerente criado: username=gerente, senha=gerente123");

        log.info("  📊 Total: 4 usuários criados (Admin, Atendente, Cozinha, Gerente)");
    }

    private void seedAtendentes() {
        log.info("👔 Verificando atendentes...");

        if (atendenteRepository.count() > 0) {
            log.info("  ┗ Atendentes já existem. Pulando...");
            return;
        }

        // 1. ADMIN (via telefone)
        Atendente admin = Atendente.builder()
            .nome("Administrador do Sistema")
            .email("admin.tel@restaurante.ao")
            .telefone("+244923000001")
            .senha(passwordEncoder.encode("admin123"))
            .tipoUsuario(TipoUsuario.ADMIN)
            .ativo(true)
            .build();
        atendenteRepository.save(admin);
        log.info("  ┣ ✅ Admin: telefone=+244923000001, senha=admin123");

        // 2. ATENDENTE (via telefone)
        Atendente atendente = Atendente.builder()
            .nome("João Silva - Atendente")
            .email("atendente.tel@restaurante.ao")
            .telefone("+244923000002")
            .senha(passwordEncoder.encode("atendente123"))
            .tipoUsuario(TipoUsuario.ATENDENTE)
            .ativo(true)
            .build();
        atendenteRepository.save(atendente);
        log.info("  ┣ ✅ Atendente: telefone=+244923000002, senha=atendente123");

        // 3. GERENTE (via telefone)
        Atendente gerente = Atendente.builder()
            .nome("Carlos Mendes - Gerente")
            .email("gerente.tel@restaurante.ao")
            .telefone("+244923000003")
            .senha(passwordEncoder.encode("gerente123"))
            .tipoUsuario(TipoUsuario.GERENTE)
            .ativo(true)
            .build();
        atendenteRepository.save(gerente);
        log.info("  ┗ ✅ Gerente: telefone=+244923000003, senha=gerente123");

        log.info("  📊 Total: 3 atendentes criados (login via TELEFONE)");
    }

    private void seedClientes() {
        log.info("👥 Verificando clientes...");

        if (clienteRepository.count() > 0) {
            log.info("  ┗ Clientes já existem. Pulando...");
            return;
        }

        // Criar 5 clientes de teste
        for (int i = 1; i <= 5; i++) {
            Cliente cliente = Cliente.builder()
                .telefone(String.format("+244%09d", 900000000 + i))
                .nome(String.format("Cliente Teste %d", i))
                .telefoneVerificado(true)
                .tipoUsuario(TipoUsuario.CLIENTE)
                .build();

            clienteRepository.save(cliente);
        }

        log.info("  ┗ ✅ 5 clientes criados");
    }

    private void seedUnidadesAtendimento() {
        log.info("🏢 Verificando unidades de atendimento...");

        if (unidadeAtendimentoRepository.count() > 0) {
            log.info("  ┗ Unidades já existem. Pulando...");
            return;
        }

        UnidadeAtendimento unidade1 = UnidadeAtendimento.builder()
            .nome("Restaurante Central")
            .tipo(TipoUnidadeAtendimento.RESTAURANTE)
            .descricao("Restaurante principal - Av. 4 de Fevereiro, Luanda")
            .ativa(true)
            .build();

        UnidadeAtendimento unidade2 = UnidadeAtendimento.builder()
            .nome("Cantina Corporativa")
            .tipo(TipoUnidadeAtendimento.CAFETERIA)
            .descricao("Cantina corporativa - Zona Industrial, Viana")
            .ativa(true)
            .build();

        unidadeAtendimentoRepository.save(unidade1);
        unidadeAtendimentoRepository.save(unidade2);
        log.info("  ┗ ✅ 2 unidades de atendimento criadas");
    }

    private void seedCozinhas() {
        log.info("🍳 Verificando cozinhas...");

        if (cozinhaRepository.count() > 0) {
            log.info("  ┗ Cozinhas já existem. Pulando...");
            return;
        }

        Cozinha cozinha1 = Cozinha.builder()
            .nome("Cozinha Principal")
            .tipo(TipoCozinha.CENTRAL)
            .descricao("Cozinha principal - pratos quentes")
            .ativa(true)
            .build();

        Cozinha cozinha2 = Cozinha.builder()
            .nome("Cozinha Fria")
            .tipo(TipoCozinha.BAR_PREP)
            .descricao("Saladas, sobremesas e bebidas")
            .ativa(true)
            .build();

        Cozinha cozinha3 = Cozinha.builder()
            .nome("Cozinha Inativa (Teste)")
            .tipo(TipoCozinha.GRILL)
            .descricao("Cozinha desativada para testes de validação")
            .ativa(false)
            .build();

        cozinhaRepository.save(cozinha1);
        cozinhaRepository.save(cozinha2);
        cozinhaRepository.save(cozinha3);
        log.info("  ┗ ✅ 3 cozinhas criadas (2 ativas, 1 inativa)");
    }

    private void seedProdutos() {
        log.info("🍽️  Verificando produtos...");

        if (produtoRepository.count() > 0) {
            log.info("  ┗ Produtos já existem. Pulando...");
            return;
        }

        // Produtos diversos
        Produto produto1 = Produto.builder()
            .codigo("PROD-001")
            .nome("Muamba de Galinha")
            .descricao("Prato tradicional angolano com galinha ao molho de óleo de palma")
            .preco(new BigDecimal("1500.00"))
            .categoria(CategoriaProduto.PRATO_PRINCIPAL)
            .disponivel(true)
            .ativo(true)
            .build();

        Produto produto2 = Produto.builder()
            .codigo("PROD-002")
            .nome("Calulu de Peixe")
            .descricao("Peixe fresco com vegetais e óleo de palma")
            .preco(new BigDecimal("1800.00"))
            .categoria(CategoriaProduto.PRATO_PRINCIPAL)
            .disponivel(true)
            .ativo(true)
            .build();

        Produto produto3 = Produto.builder()
            .codigo("PROD-003")
            .nome("Funge com Feijão")
            .descricao("Funge tradicional acompanhado de feijão temperado")
            .preco(new BigDecimal("800.00"))
            .categoria(CategoriaProduto.ACOMPANHAMENTO)
            .disponivel(true)
            .ativo(true)
            .build();

        Produto produto4 = Produto.builder()
            .codigo("PROD-004")
            .nome("Salada Tropical")
            .descricao("Mix de folhas, abacate e manga")
            .preco(new BigDecimal("600.00"))
            .categoria(CategoriaProduto.ENTRADA)
            .disponivel(true)
            .ativo(true)
            .build();

        Produto produto5 = Produto.builder()
            .codigo("PROD-005")
            .nome("Sumo Natural de Maracujá")
            .descricao("Sumo natural de maracujá fresco")
            .preco(new BigDecimal("300.00"))
            .categoria(CategoriaProduto.BEBIDA_NAO_ALCOOLICA)
            .disponivel(true)
            .ativo(true)
            .build();

        Produto produto6 = Produto.builder()
            .codigo("PROD-006")
            .nome("Cerveja Cuca (350ml)")
            .descricao("Cerveja Cuca gelada")
            .preco(new BigDecimal("250.00"))
            .categoria(CategoriaProduto.BEBIDA_ALCOOLICA)
            .disponivel(true)
            .ativo(true)
            .build();

        // Produto inativo (para testes)
        Produto produto7 = Produto.builder()
            .codigo("PROD-999")
            .nome("Produto Indisponível (Teste)")
            .descricao("Produto desativado para testes de validação")
            .preco(new BigDecimal("100.00"))
            .categoria(CategoriaProduto.OUTROS)
            .disponivel(false)
            .ativo(true)
            .build();

        produtoRepository.save(produto1);
        produtoRepository.save(produto2);
        produtoRepository.save(produto3);
        produtoRepository.save(produto4);
        produtoRepository.save(produto5);
        produtoRepository.save(produto6);
        produtoRepository.save(produto7);

        log.info("  ┗ ✅ 7 produtos criados (6 disponíveis, 1 indisponível)");
    }

    private void seedUnidadesConsumo() {
        log.info("👤 Verificando unidades de consumo...");

        if (unidadeDeConsumoRepository.count() > 0) {
            log.info("  ┗ Unidades de consumo já existem. Pulando...");
            return;
        }

        var unidadeAtendimento = unidadeAtendimentoRepository.findAll().get(0);
        var clientes = clienteRepository.findAll();

        // Criar 5 unidades de consumo de teste (mesas) - uma para cada cliente
        for (int i = 0; i < Math.min(5, clientes.size()); i++) {
            UnidadeDeConsumo unidade = UnidadeDeConsumo.builder()
                .referencia(String.format("Mesa %d", i + 1))
                .tipo(TipoUnidadeConsumo.MESA_FISICA)
                .numero(i + 1)
                .status(StatusUnidadeConsumo.OCUPADA)
                .unidadeAtendimento(unidadeAtendimento)
                .cliente(clientes.get(i))
                .build();

            unidadeDeConsumoRepository.save(unidade);
        }

        log.info("  ┗ ✅ {} unidades de consumo criadas", Math.min(5, clientes.size()));
    }
}
