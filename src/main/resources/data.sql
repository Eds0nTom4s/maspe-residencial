-- Script de inicialização do banco de dados
-- Sistema de Restauração

-- Criar banco de dados (executar como superuser)
-- CREATE DATABASE restaurante_db;

-- ========================================
-- DADOS INICIAIS - PRODUTOS (CARDÁPIO)
-- ========================================

-- Entradas
INSERT INTO produtos (codigo, nome, descricao, preco, categoria, disponivel, ativo, created_at, updated_at)
VALUES 
('ENT001', 'Pastéis de Bacalhau', 'Pastéis de bacalhau desfiado crocantes', 3500.00, 'ENTRADA', true, true, NOW(), NOW()),
('ENT002', 'Salada Tropical', 'Mix de folhas com manga, abacate e molho de maracujá', 2800.00, 'ENTRADA', true, true, NOW(), NOW()),
('ENT003', 'Pataniscas de Bacalhau', 'Bolinhos de bacalhau com molho de piri-piri', 4200.00, 'ENTRADA', true, true, NOW(), NOW());

-- Pratos Principais
INSERT INTO produtos (codigo, nome, descricao, preco, categoria, tempo_preparo_minutos, disponivel, ativo, created_at, updated_at)
VALUES 
('PRATO001', 'Muamba de Galinha', 'Galinha refogada com quiabo e dendê', 8500.00, 'PRATO_PRINCIPAL', 35, true, true, NOW(), NOW()),
('PRATO002', 'Calulu de Peixe', 'Peixe fresco com batata doce, quiabo e óleo de palma', 9200.00, 'PRATO_PRINCIPAL', 30, true, true, NOW(), NOW()),
('PRATO003', 'Cabidela de Frango', 'Frango cozido no próprio sangue com arroz', 7800.00, 'PRATO_PRINCIPAL', 40, true, true, NOW(), NOW()),
('PRATO004', 'Churrasco Misto', 'Picanha, linguiça e frango grelhados', 12500.00, 'PRATO_PRINCIPAL', 35, true, true, NOW(), NOW());

-- Pizzas
INSERT INTO produtos (codigo, nome, descricao, preco, categoria, tempo_preparo_minutos, disponivel, ativo, created_at, updated_at)
VALUES 
('PIZZA001', 'Pizza Margherita', 'Molho de tomate, mussarela, manjericão', 6500.00, 'PIZZA', 20, true, true, NOW(), NOW()),
('PIZZA002', 'Pizza Frango com Catupiry', 'Frango desfiado, catupiry e azeitonas', 7200.00, 'PIZZA', 20, true, true, NOW(), NOW()),
('PIZZA003', 'Pizza Quatro Queijos', 'Mussarela, gorgonzola, parmesão, provolone', 7800.00, 'PIZZA', 20, true, true, NOW(), NOW());

-- Sobremesas
INSERT INTO produtos (codigo, nome, descricao, preco, categoria, tempo_preparo_minutos, disponivel, ativo, created_at, updated_at)
VALUES 
('SOB001', 'Cocada Angolana', 'Cocada cremosa tradicional', 2500.00, 'SOBREMESA', 10, true, true, NOW(), NOW()),
('SOB002', 'Bolo de Ginguba', 'Bolo de amendoim com cobertura de chocolate', 2800.00, 'SOBREMESA', 10, true, true, NOW(), NOW()),
('SOB003', 'Mousse de Maracujá', 'Mousse cremoso de maracujá com calda', 3200.00, 'SOBREMESA', 15, true, true, NOW(), NOW());

-- Bebidas Não Alcoólicas
INSERT INTO produtos (codigo, nome, descricao, preco, categoria, disponivel, ativo, created_at, updated_at)
VALUES 
('BEB001', 'Refrigerante Lata', 'Coca-Cola, Pepsi, Fanta (350ml)', 800.00, 'BEBIDA_NAO_ALCOOLICA', true, true, NOW(), NOW()),
('BEB002', 'Sumo Natural', 'Laranja, Maracujá, Abacaxi (500ml)', 1500.00, 'BEBIDA_NAO_ALCOOLICA', true, true, NOW(), NOW()),
('BEB003', 'Água Mineral', 'Com ou sem gás (500ml)', 600.00, 'BEBIDA_NAO_ALCOOLICA', true, true, NOW(), NOW()),
('BEB004', 'Kissangua', 'Bebida tradicional de milho fermentado', 1200.00, 'BEBIDA_NAO_ALCOOLICA', true, true, NOW(), NOW());

-- Bebidas Alcoólicas
INSERT INTO produtos (codigo, nome, descricao, preco, categoria, disponivel, ativo, created_at, updated_at)
VALUES 
('ALC001', 'Cerveja Cuca', 'Cerveja angolana (330ml)', 1500.00, 'BEBIDA_ALCOOLICA', true, true, NOW(), NOW()),
('ALC002', 'Cerveja Ngola', 'Cerveja angolana premium (330ml)', 1800.00, 'BEBIDA_ALCOOLICA', true, true, NOW(), NOW()),
('ALC003', 'Caipirinha', 'Cachaça, limão, açúcar', 2500.00, 'BEBIDA_ALCOOLICA', true, true, NOW(), NOW()),
('ALC004', 'Vinho Português Taça', 'Vinho tinto ou branco (150ml)', 3200.00, 'BEBIDA_ALCOOLICA', true, true, NOW(), NOW());

-- ========================================
-- DADOS INICIAIS - ATENDENTES
-- ========================================

-- SENHAS DE TESTE (NÃO USAR EM PRODUÇÃO):
-- António: senha123
-- Maria: senha123
-- José (Gerente): gerente123
-- Admin (Gerente): admin123

INSERT INTO atendentes (nome, email, telefone, senha, tipo_usuario, ativo, created_at, updated_at)
VALUES 
('António Domingos', 'antonio.domingos@restaurante.ao', '+244923456789', '$2b$12$ymMq0Jx4QCg3Lnv63iiiZuVBX.CR331.dFRDTlXewsNTTY1aZd1j.', 'ATENDENTE', true, NOW(), NOW()),
('Maria da Conceição', 'maria.conceicao@restaurante.ao', '+244945678901', '$2b$12$ymMq0Jx4QCg3Lnv63iiiZuVBX.CR331.dFRDTlXewsNTTY1aZd1j.', 'ATENDENTE', true, NOW(), NOW()),
('José Manuel', 'jose.manuel@restaurante.ao', '+244912345678', '$2b$12$ADUm/W1Wr7sba5f0axAh7OXQI1tVU/cXMnfownckl79F8t2UGGXVG', 'GERENTE', true, NOW(), NOW()),
('Admin Sistema', 'admin@restaurante.ao', '+244999999999', '$2b$12$GjvA5qNqQ.ygizKg3S5IpOI//uxHUhDgt2MnRdlMzStqASYzAWpEq', 'GERENTE', true, NOW(), NOW());

-- ========================================
-- DADOS INICIAIS - COZINHAS
-- ========================================

INSERT INTO cozinhas (nome, tipo, descricao, ativa, created_at, updated_at)
VALUES 
('Cozinha Principal', 'CENTRAL', 'Cozinha principal - Pratos quentes e grelhados', true, NOW(), NOW()),
('Pizzaria', 'PIZZARIA', 'Forno para pizzas e massas', true, NOW(), NOW()),
('Confeitaria', 'CONFEITARIA', 'Preparação de sobremesas e saladas', true, NOW(), NOW()),
('Bar e Bebidas', 'BAR_PREP', 'Preparação de bebidas e petiscos', true, NOW(), NOW());

-- ========================================
-- DADOS INICIAIS - UNIDADES DE ATENDIMENTO (SETORES)
-- ========================================

-- Unidades de Atendimento representam setores do estabelecimento
INSERT INTO unidades_atendimento (nome, tipo, descricao, ativa, created_at, updated_at)
VALUES 
('Salão Principal', 'RESTAURANTE', 'Área principal do restaurante com 20 mesas', true, NOW(), NOW()),
('Bar Angolano', 'BAR', 'Bar com petiscos e bebidas típicas', true, NOW(), NOW()),
('Esplanada', 'LOUNGE', 'Área externa com vista', true, NOW(), NOW());

-- Relacionar Unidades de Atendimento com Cozinhas
-- Salão Principal -> Todas as cozinhas
INSERT INTO unidade_cozinha (unidade_id, cozinha_id)
SELECT ua.id, c.id 
FROM unidades_atendimento ua, cozinhas c
WHERE ua.nome = 'Salão Principal';

-- Bar -> Cozinha Bar e Confeitaria
INSERT INTO unidade_cozinha (unidade_id, cozinha_id)
SELECT ua.id, c.id 
FROM unidades_atendimento ua, cozinhas c
WHERE ua.nome = 'Bar Angolano' 
  AND c.tipo IN ('BAR_PREP', 'CONFEITARIA');

-- Esplanada -> Todas as cozinhas
INSERT INTO unidade_cozinha (unidade_id, cozinha_id)
SELECT ua.id, c.id 
FROM unidades_atendimento ua, cozinhas c
WHERE ua.nome = 'Esplanada';

-- ========================================
-- DADOS INICIAIS - CLIENTES DE TESTE
-- ========================================

-- Clientes de exemplo para testes
-- Cliente é identificado pelo telefone e autenticado via OTP
INSERT INTO clientes (nome, telefone, telefone_verificado, tipo_usuario, ativo, created_at, updated_at)
VALUES 
('João Pedro Afonso', '+244923111222', true, 'CLIENTE', true, NOW(), NOW()),
('Ana Maria Sebastião', '+244945222333', true, 'CLIENTE', true, NOW(), NOW()),
('Carlos Alberto Neto', '+244912333444', true, 'CLIENTE', true, NOW(), NOW());

-- ========================================
-- DADOS INICIAIS - CONFIGURAÇÃO FINANCEIRA
-- ========================================

-- Configuração global do sistema financeiro
-- Controla se pós-pago está ativo no sistema
INSERT INTO configuracao_financeira_sistema (
    pos_pago_ativo,
    atualizado_por_nome,
    atualizado_por_role,
    created_at, 
    updated_at
)
VALUES 
(true, 'Sistema', 'ADMIN', NOW(), NOW());

-- ========================================
-- OBSERVAÇÕES
-- ========================================

/*
IMPORTANTE:
1. Este script popula dados iniciais para desenvolvimento e testes
2. As senhas dos atendentes devem ser hasheadas usando BCrypt na produção
3. Os clientes podem ser criados automaticamente via OTP ou pré-cadastrados
4. UnidadeAtendimento representa SETORES (Restaurante, Bar), não mesas individuais
5. UnidadeDeConsumo representa MESAS/COMANDAS e são criadas dinamicamente
6. As cozinhas são vinculadas aos setores via tabela unidade_cozinha
7. ConfiguracaoFinanceiraSistema controla apenas se pós-pago está ativo globalmente
8. Preços em Kwanzas Angolanos (AOA) - valores aproximados baseados em 2026
9. Números de telefone angolanos: formato +244 + 9 dígitos (Unitel 923/924, Movicel 945/946, Africell 947/948)
10. Pratos típicos angolanos incluídos no cardápio

DADOS POPULADOS:
✅ produtos (17 itens do cardápio)
✅ atendentes (3 usuários: 2 atendentes + 1 gerente)
✅ cozinhas (4 cozinhas configuradas por tipo)
✅ unidades_atendimento (3 setores: Salão, Bar, Esplanada)
✅ unidade_cozinha (relacionamentos entre setores e cozinhas)
✅ clientes (3 clientes de teste)
✅ configuracao_financeira_sistema (pós-pago ativo)

DADOS CRIADOS DINAMICAMENTE:
- unidades_consumo: Criadas quando cliente faz login (representam mesas/comandas)
- pedidos: Criados durante atendimento
- itens_pedido: Adicionados aos pedidos
- sub_pedidos: Criados automaticamente para cada item
- pagamentos: Gerados no fechamento da conta
- fundos_consumo: Criados para modalidade pré-pago
- transacoes_fundo: Movimentações financeiras
- event_logs: Auditoria automática
- qr_code_tokens: Tokens temporários para acesso

RELACIONAMENTOS PRINCIPAIS:
1. Cliente (1) ←→ (N) UnidadeDeConsumo [Mesa/Comanda virtual]
2. UnidadeAtendimento [Setor] (1) ←→ (N) UnidadeDeConsumo
3. UnidadeAtendimento (N) ←→ (N) Cozinha [Many-to-Many via unidade_cozinha]
4. UnidadeDeConsumo (1) ←→ (N) Pedido
5. Pedido (1) ←→ (N) ItemPedido ←→ (1) Produto
6. ItemPedido (1) ←→ (1) SubPedido ←→ (1) Cozinha
7. Pedido (1) ←→ (N) Pagamento
8. UnidadeDeConsumo (1) ←→ (1) FundoConsumo ←→ (N) TransacaoFundo

PRÓXIMOS PASSOS:
- Implementar geração de QR Codes para acesso rápido
- Configurar serviço de SMS/WhatsApp para envio de OTP
- Integrar com gateway de pagamento angolano
- Implementar sistema de backup automático

COMANDOS ÚTEIS:
-- Ver todos os produtos disponíveis por categoria
SELECT codigo, nome, preco, categoria, tempo_preparo_minutos 
FROM produtos 
WHERE disponivel = true AND ativo = true 
ORDER BY categoria, nome;

-- Ver todas as unidades de atendimento (setores) com cozinhas
SELECT 
    ua.id,
    ua.nome,
    ua.tipo,
    ua.descricao,
    COUNT(uc.cozinha_id) as total_cozinhas
FROM unidades_atendimento ua
LEFT JOIN unidade_cozinha uc ON ua.id = uc.unidade_id
WHERE ua.ativa = true
GROUP BY ua.id, ua.nome, ua.tipo, ua.descricao
ORDER BY ua.nome;

-- Ver todas as cozinhas com seus tipos
SELECT id, nome, tipo, descricao, ativa
FROM cozinhas
WHERE ativa = true
ORDER BY tipo, nome;

-- Ver configuração financeira atual
SELECT pos_pago_ativo, atualizado_por_nome, atualizado_por_role, updated_at
FROM configuracao_financeira_sistema
ORDER BY updated_at DESC
LIMIT 1;

-- Ver clientes cadastrados
SELECT id, nome, telefone, email, ativo
FROM clientes
WHERE ativo = true
ORDER BY nome;

-- Ver unidades de consumo abertas (mesas/comandas ativas)
SELECT 
    uc.id, 
    uc.referencia, 
    uc.tipo, 
    uc.status, 
    c.nome as cliente,
    c.telefone,
    ua.nome as setor,
    uc.aberta_em 
FROM unidades_consumo uc 
INNER JOIN clientes c ON uc.cliente_id = c.id 
LEFT JOIN unidades_atendimento ua ON uc.unidade_atendimento_id = ua.id
WHERE uc.status != 'FINALIZADA' 
ORDER BY uc.referencia;

-- Ver pedidos ativos com detalhes
SELECT 
    p.numero, 
    p.status, 
    uc.referencia as unidade_consumo,
    ua.nome as setor,
    p.total, 
    p.created_at 
FROM pedidos p 
INNER JOIN unidades_consumo uc ON p.unidade_consumo_id = uc.id
LEFT JOIN unidades_atendimento ua ON uc.unidade_atendimento_id = ua.id
WHERE p.status IN ('PENDENTE', 'RECEBIDO', 'EM_PREPARO') 
ORDER BY p.created_at;
*/
