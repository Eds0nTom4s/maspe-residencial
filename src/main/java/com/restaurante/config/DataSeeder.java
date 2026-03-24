package com.restaurante.config;

import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * DataSeeder
 */
@Component
@Profile("dev")
@Order(100)
@RequiredArgsConstructor
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final AtendenteRepository atendenteRepository;
    private final CozinhaRepository cozinhaRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final ProdutoRepository produtoRepository;
    private final MesaRepository mesaRepository;
    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final InstituicaoRepository instituicaoRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Ponto de entrada
    // ─────────────────────────────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        log.info("=".repeat(72));
        log.info("🌱  DATA SEEDER — Perfil: dev");
        log.info("=".repeat(72));

        seedInstituicao();
        seedUsuarios();
        seedAtendentes();
        seedClientes();
        seedCozinhas();
        seedUnidadesAtendimento();   // depende das cozinhas já persistidas
        seedProdutos();
        seedMesas();                 // depende das unidades já persistidas

        log.info("=".repeat(72));
        log.info("✅  DATA SEEDER CONCLUÍDO");
        log.info("=".repeat(72));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Instituição Base (Tenant)
    // ─────────────────────────────────────────────────────────────────────────

    private void seedInstituicao() {
        if (instituicaoRepository.count() > 0) {
            log.info("  [instituicao]     já existe — pulando");
            return;
        }

        Instituicao inst = Instituicao.builder()
            .nome("MesaDigital / MASPE")
            .sigla("MASPE")
            .nif("5000000000") // NIF fictício
            .urlLogo("https://maspe.ao/logo.png")
            .ativa(true)
            .build();

        instituicaoRepository.save(inst);
        log.info("  [instituicao]     ✅ 1 criada  (MASPE)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Usuários Spring Security
    // ─────────────────────────────────────────────────────────────────────────

    private void seedUsuarios() {
        if (userRepository.count() > 0) {
            log.info("  [usuarios]        já existem — pulando");
            return;
        }

        userRepository.saveAll(List.of(
            User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .nomeCompleto("Administrador do Sistema")
                .telefone("+244923456789")
                .email("admin@restaurante.ao")
                .roles(Set.of(Role.ROLE_ADMIN))
                .ativo(true).build(),

            User.builder()
                .username("atendente")
                .password(passwordEncoder.encode("atendente123"))
                .nomeCompleto("João Silva")
                .telefone("+244923111222")
                .email("atendente@restaurante.ao")
                .roles(Set.of(Role.ROLE_ATENDENTE))
                .ativo(true).build(),

            User.builder()
                .username("cozinha")
                .password(passwordEncoder.encode("cozinha123"))
                .nomeCompleto("Maria Santos")
                .telefone("+244923222333")
                .email("cozinha@restaurante.ao")
                .roles(Set.of(Role.ROLE_COZINHA))
                .ativo(true).build(),

            User.builder()
                .username("gerente")
                .password(passwordEncoder.encode("gerente123"))
                .nomeCompleto("Carlos Mendes")
                .telefone("+244923333444")
                .email("gerente@restaurante.ao")
                .roles(Set.of(Role.ROLE_GERENTE))
                .ativo(true).build()
        ));

        log.info("  [usuarios]        ✅ 4 criados  (admin / atendente / cozinha / gerente)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Atendentes (login por telefone)
    // ─────────────────────────────────────────────────────────────────────────

    private void seedAtendentes() {
        if (atendenteRepository.count() > 0) {
            log.info("  [atendentes]      já existem — pulando");
            return;
        }

        atendenteRepository.saveAll(List.of(
            Atendente.builder()
                .nome("Administrador do Sistema")
                .email("admin.tel@restaurante.ao")
                .telefone("+244923000001")
                .senha(passwordEncoder.encode("admin123"))
                .tipoUsuario(TipoUsuario.ADMIN)
                .ativo(true).build(),

            Atendente.builder()
                .nome("João Silva")
                .email("atendente.tel@restaurante.ao")
                .telefone("+244923000002")
                .senha(passwordEncoder.encode("atendente123"))
                .tipoUsuario(TipoUsuario.ATENDENTE)
                .ativo(true).build(),

            Atendente.builder()
                .nome("Carlos Mendes")
                .email("gerente.tel@restaurante.ao")
                .telefone("+244923000003")
                .senha(passwordEncoder.encode("gerente123"))
                .tipoUsuario(TipoUsuario.GERENTE)
                .ativo(true).build()
        ));

        log.info("  [atendentes]      ✅ 3 criados  (+244923000001 / +244923000002 / +244923000003)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Clientes de teste
    // ─────────────────────────────────────────────────────────────────────────

    private void seedClientes() {
        if (clienteRepository.count() > 0) {
            log.info("  [clientes]        já existem — pulando");
            return;
        }

        clienteRepository.saveAll(List.of(
            cliente("+244923111222", "João Pedro Afonso"),
            cliente("+244945222333", "Ana Maria Sebastião"),
            cliente("+244912333444", "Carlos Alberto Neto"),
            cliente("+244947444555", "Fernanda Lopes Dias"),
            cliente("+244924555666", "Paulo Eduardo Ferreira")
        ));

        log.info("  [clientes]        ✅ 5 criados");
    }

    private Cliente cliente(String telefone, String nome) {
        return Cliente.builder()
            .telefone(telefone)
            .nome(nome)
            .telefoneVerificado(true)
            .tipoUsuario(TipoUsuario.CLIENTE)
            .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cozinhas
    // ─────────────────────────────────────────────────────────────────────────

    private void seedCozinhas() {
        if (cozinhaRepository.count() > 0) {
            log.info("  [cozinhas]        já existem — pulando");
            return;
        }

        cozinhaRepository.saveAll(List.of(
            cozinha("Cozinha Principal",  TipoCozinha.CENTRAL,      "Pratos quentes e grelhados angolanos", true),
            cozinha("Pizzaria",           TipoCozinha.PIZZARIA,     "Forno para pizzas e massas",           true),
            cozinha("Confeitaria",        TipoCozinha.CONFEITARIA,  "Sobremesas, saladas e entradas",       true),
            cozinha("Bar e Bebidas",      TipoCozinha.BAR_PREP,     "Bebidas, cocktails e petiscos",        true),
            cozinha("Churrasqueira",      TipoCozinha.GRILL,        "Grelhados e churrascos (inativo)",     false)
        ));

        log.info("  [cozinhas]        ✅ 5 criadas  (4 ativas + 1 inativa para testes)");
    }

    private Cozinha cozinha(String nome, TipoCozinha tipo, String descricao, boolean ativa) {
        return Cozinha.builder()
            .nome(nome).tipo(tipo).descricao(descricao).ativa(ativa).build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unidades de Atendimento + relacionamentos com Cozinhas
    // ─────────────────────────────────────────────────────────────────────────

    private void seedUnidadesAtendimento() {
        if (unidadeAtendimentoRepository.count() > 0) {
            log.info("  [unid.atendimento] já existem — pulando");
            return;
        }

        Cozinha cozinhaPrincipal = cozinhaRepository.findByNomeIgnoreCase("Cozinha Principal").orElseThrow();
        Cozinha pizzaria         = cozinhaRepository.findByNomeIgnoreCase("Pizzaria").orElseThrow();
        Cozinha confeitaria      = cozinhaRepository.findByNomeIgnoreCase("Confeitaria").orElseThrow();
        Cozinha bar              = cozinhaRepository.findByNomeIgnoreCase("Bar e Bebidas").orElseThrow();

        // Salão Principal → todas as 4 cozinhas ativas
        UnidadeAtendimento salao = UnidadeAtendimento.builder()
            .nome("Salão Principal")
            .tipo(TipoUnidadeAtendimento.RESTAURANTE)
            .descricao("Área principal do restaurante — Av. 4 de Fevereiro, Luanda")
            .ativa(true).build();
        salao.adicionarCozinha(cozinhaPrincipal);
        salao.adicionarCozinha(pizzaria);
        salao.adicionarCozinha(confeitaria);
        salao.adicionarCozinha(bar);
        unidadeAtendimentoRepository.save(salao);

        // Bar Angolano → Bar e Confeitaria
        UnidadeAtendimento barAngolano = UnidadeAtendimento.builder()
            .nome("Bar Angolano")
            .tipo(TipoUnidadeAtendimento.BAR)
            .descricao("Bar com petiscos e bebidas típicas angolanas")
            .ativa(true).build();
        barAngolano.adicionarCozinha(bar);
        barAngolano.adicionarCozinha(confeitaria);
        unidadeAtendimentoRepository.save(barAngolano);

        // Esplanada → Cozinha Principal e Bar
        UnidadeAtendimento esplanada = UnidadeAtendimento.builder()
            .nome("Esplanada")
            .tipo(TipoUnidadeAtendimento.LOUNGE)
            .descricao("Área externa com vista — serviço simplificado")
            .ativa(true).build();
        esplanada.adicionarCozinha(cozinhaPrincipal);
        esplanada.adicionarCozinha(bar);
        unidadeAtendimentoRepository.save(esplanada);

        log.info("  [unid.atendimento] ✅ 3 criadas  (Salão/Bar/Esplanada + cozinhas associadas)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cardápio completo (produtos)
    // ─────────────────────────────────────────────────────────────────────────

    private void seedProdutos() {
        if (produtoRepository.count() > 0) {
            log.info("  [produtos]        já existem — pulando");
            return;
        }

        produtoRepository.saveAll(List.of(

            // ── Entradas ─────────────────────────────────────────────────────
            produto("ENT-001", "Pastéis de Bacalhau",    "Pastéis de bacalhau desfiado crocantes",            3500, null, CategoriaProduto.ENTRADA, true),
            produto("ENT-002", "Salada Tropical",        "Mix de folhas, manga, abacate e molho de maracujá", 2800, null, CategoriaProduto.ENTRADA, true),
            produto("ENT-003", "Pataniscas de Bacalhau", "Bolinhos de bacalhau com molho de piri-piri",       4200, null, CategoriaProduto.ENTRADA, true),

            // ── Pratos Principais ─────────────────────────────────────────────
            produto("PRATO-001", "Muamba de Galinha",  "Galinha refogada com quiabo e dendê",                 8500, 35, CategoriaProduto.PRATO_PRINCIPAL, true),
            produto("PRATO-002", "Calulu de Peixe",    "Peixe fresco com batata doce, quiabo e óleo de palma",9200, 30, CategoriaProduto.PRATO_PRINCIPAL, true),
            produto("PRATO-003", "Cabidela de Frango", "Frango no próprio sangue com arroz",                  7800, 40, CategoriaProduto.PRATO_PRINCIPAL, true),
            produto("PRATO-004", "Churrasco Misto",    "Picanha, linguiça e frango grelhados",               12500, 35, CategoriaProduto.PRATO_PRINCIPAL, true),

            // ── Acompanhamentos ───────────────────────────────────────────────
            produto("ACOMP-001", "Funge com Feijão",  "Funge tradicional com feijão temperado",               800,  15, CategoriaProduto.ACOMPANHAMENTO, true),
            produto("ACOMP-002", "Arroz Branco",      "Arroz cozido no ponto",                                 500,  10, CategoriaProduto.ACOMPANHAMENTO, true),

            // ── Pizzas ────────────────────────────────────────────────────────
            produto("PIZZA-001", "Pizza Margherita",          "Molho de tomate, mussarela, manjericão",          6500, 20, CategoriaProduto.PIZZA, true),
            produto("PIZZA-002", "Pizza Frango com Catupiry", "Frango desfiado, catupiry e azeitonas",           7200, 20, CategoriaProduto.PIZZA, true),
            produto("PIZZA-003", "Pizza Quatro Queijos",      "Mussarela, gorgonzola, parmesão, provolone",      7800, 20, CategoriaProduto.PIZZA, true),

            // ── Sobremesas ────────────────────────────────────────────────────
            produto("SOB-001", "Cocada Angolana",    "Cocada cremosa tradicional angolana",        2500, 10, CategoriaProduto.SOBREMESA, true),
            produto("SOB-002", "Bolo de Ginguba",    "Bolo de amendoim com cobertura de chocolate",2800, 10, CategoriaProduto.SOBREMESA, true),
            produto("SOB-003", "Mousse de Maracujá", "Mousse cremoso de maracujá com calda",       3200, 15, CategoriaProduto.SOBREMESA, true),

            // ── Bebidas Não Alcoólicas ────────────────────────────────────────
            produto("BEB-001", "Refrigerante Lata",  "Coca-Cola, Pepsi, Fanta (350ml)",            800,  null, CategoriaProduto.BEBIDA_NAO_ALCOOLICA, true),
            produto("BEB-002", "Sumo Natural",       "Laranja, Maracujá ou Abacaxi (500ml)",      1500,  null, CategoriaProduto.BEBIDA_NAO_ALCOOLICA, true),
            produto("BEB-003", "Água Mineral",       "Com ou sem gás (500ml)",                     600,  null, CategoriaProduto.BEBIDA_NAO_ALCOOLICA, true),
            produto("BEB-004", "Kissangua",          "Bebida tradicional de milho fermentado",    1200,  null, CategoriaProduto.BEBIDA_NAO_ALCOOLICA, true),

            // ── Bebidas Alcoólicas ────────────────────────────────────────────
            produto("ALC-001", "Cerveja Cuca",          "Cerveja angolana (330ml)",                1500, null, CategoriaProduto.BEBIDA_ALCOOLICA, true),
            produto("ALC-002", "Cerveja Ngola",         "Cerveja angolana premium (330ml)",        1800, null, CategoriaProduto.BEBIDA_ALCOOLICA, true),
            produto("ALC-003", "Caipirinha",            "Cachaça, limão, açúcar",                  2500, null, CategoriaProduto.BEBIDA_ALCOOLICA, true),
            produto("ALC-004", "Vinho Português (taça)","Tinto ou branco (150ml)",                 3200, null, CategoriaProduto.BEBIDA_ALCOOLICA, true),

            // ── Produto inativo — para testes de validação ───────────────────
            produto("TEST-999", "Produto Indisponível (Teste)", "Usado em testes — nunca deve aparecer no cardápio", 100, null, CategoriaProduto.OUTROS, false)
        ));

        log.info("  [produtos]        ✅ 23 criados  (22 disponíveis + 1 inativo para testes)");
    }

    private Produto produto(String codigo, String nome, String descricao,
                            int preco, Integer tempoPreparo,
                            CategoriaProduto categoria, boolean disponivel) {
        return Produto.builder()
            .codigo(codigo)
            .nome(nome)
            .descricao(descricao)
            .preco(new BigDecimal(preco))
            .tempoPreparoMinutos(tempoPreparo)
            .categoria(categoria)
            .disponivel(disponivel)
            .ativo(true)
            .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mesas físicas permanentes (sem status — derivado em runtime)
    // ─────────────────────────────────────────────────────────────────────────

    private void seedMesas() {
        if (mesaRepository.count() > 0) {
            log.info("  [mesas]           já existem — pulando");
            return;
        }

        UnidadeAtendimento salao       = unidadeAtendimentoRepository.findByNomeIgnoreCase("Salão Principal").orElseThrow();
        UnidadeAtendimento barAngolano = unidadeAtendimentoRepository.findByNomeIgnoreCase("Bar Angolano").orElseThrow();
        UnidadeAtendimento esplanada   = unidadeAtendimentoRepository.findByNomeIgnoreCase("Esplanada").orElseThrow();

        // Salão Principal — 10 mesas (capacidade 4 pessoas)
        for (int i = 1; i <= 10; i++) {
            mesaRepository.save(mesa(i, "Mesa " + i, 4, salao));
        }

        // Bar Angolano — 4 banquetas/mesas de bar (capacidade 2 pessoas)
        for (int i = 1; i <= 4; i++) {
            mesaRepository.save(mesa(100 + i, "Bar " + i, 2, barAngolano));
        }

        // Esplanada — 6 mesas externas (capacidade 4 pessoas)
        for (int i = 1; i <= 6; i++) {
            mesaRepository.save(mesa(200 + i, "Esplanada " + i, 4, esplanada));
        }

        log.info("  [mesas]           ✅ 20 criadas  (10 Salão + 4 Bar + 6 Esplanada — status derivado)");
    }

    private Mesa mesa(int numero, String referencia, int capacidade, UnidadeAtendimento unidade) {
        return Mesa.builder()
            .numero(numero)
            .referencia(referencia)
            .tipo(TipoUnidadeConsumo.MESA_FISICA)
            .capacidade(capacidade)
            .ativa(true)
            .unidadeAtendimento(unidade)
            .build();
    }
}

