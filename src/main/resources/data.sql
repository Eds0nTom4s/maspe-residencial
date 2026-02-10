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
('ENT001', 'Bruschetta', 'Pão italiano com tomate, manjericão e azeite', 18.90, 'ENTRADA', true, true, NOW(), NOW()),
('ENT002', 'Carpaccio', 'Fatias finas de carne com alcaparras e queijo', 32.90, 'ENTRADA', true, true, NOW(), NOW()),
('ENT003', 'Salada Caesar', 'Alface romana, croutons, parmesão e molho caesar', 24.90, 'ENTRADA', true, true, NOW(), NOW());

-- Pratos Principais
INSERT INTO produtos (codigo, nome, descricao, preco, categoria, tempo_preparo_minutos, disponivel, ativo, created_at, updated_at)
VALUES 
('PRATO001', 'Filé Mignon ao Molho Madeira', 'Filé mignon grelhado com molho madeira', 68.90, 'PRATO_PRINCIPAL', 35, true, true, NOW(), NOW()),
('PRATO002', 'Salmão Grelhado', 'Salmão grelhado com legumes', 62.90, 'PRATO_PRINCIPAL', 30, true, true, NOW(), NOW()),
('PRATO003', 'Risoto de Funghi', 'Risoto cremoso com cogumelos variados', 48.90, 'PRATO_PRINCIPAL', 25, true, true, NOW(), NOW()),
('PRATO004', 'Picanha na Brasa', 'Picanha grelhada com acompanhamentos', 78.90, 'PRATO_PRINCIPAL', 40, true, true, NOW(), NOW());

-- Pizzas
INSERT INTO produtos (codigo, nome, descricao, preco, categoria, tempo_preparo_minutos, disponivel, ativo, created_at, updated_at)
VALUES 
('PIZZA001', 'Pizza Margherita', 'Molho de tomate, mussarela, manjericão', 42.90, 'PIZZA', 20, true, true, NOW(), NOW()),
('PIZZA002', 'Pizza Pepperoni', 'Molho de tomate, mussarela, pepperoni', 48.90, 'PIZZA', 20, true, true, NOW(), NOW()),
('PIZZA003', 'Pizza Quatro Queijos', 'Mussarela, gorgonzola, parmesão, provolone', 52.90, 'PIZZA', 20, true, true, NOW(), NOW());

-- Sobremesas
INSERT INTO produtos (codigo, nome, descricao, preco, categoria, tempo_preparo_minutos, disponivel, ativo, created_at, updated_at)
VALUES 
('SOB001', 'Petit Gateau', 'Bolinho de chocolate com sorvete', 22.90, 'SOBREMESA', 15, true, true, NOW(), NOW()),
('SOB002', 'Tiramisu', 'Sobremesa italiana com café e mascarpone', 18.90, 'SOBREMESA', 10, true, true, NOW(), NOW()),
('SOB003', 'Cheesecake', 'Torta de queijo com calda de frutas vermelhas', 19.90, 'SOBREMESA', 10, true, true, NOW(), NOW());

-- Bebidas Não Alcoólicas
INSERT INTO produtos (codigo, nome, descricao, preco, categoria, disponivel, ativo, created_at, updated_at)
VALUES 
('BEB001', 'Refrigerante Lata', 'Coca-Cola, Guaraná, Fanta (350ml)', 6.90, 'BEBIDA_NAO_ALCOOLICA', true, true, NOW(), NOW()),
('BEB002', 'Suco Natural', 'Laranja, Limão, Abacaxi (500ml)', 12.90, 'BEBIDA_NAO_ALCOOLICA', true, true, NOW(), NOW()),
('BEB003', 'Água Mineral', 'Com ou sem gás (500ml)', 4.90, 'BEBIDA_NAO_ALCOOLICA', true, true, NOW(), NOW()),
('BEB004', 'Limonada Suíça', 'Limonada com leite condensado', 14.90, 'BEBIDA_NAO_ALCOOLICA', true, true, NOW(), NOW());

-- Bebidas Alcoólicas
INSERT INTO produtos (codigo, nome, descricao, preco, categoria, disponivel, ativo, created_at, updated_at)
VALUES 
('ALC001', 'Cerveja Long Neck', 'Heineken, Budweiser, Corona (330ml)', 12.90, 'BEBIDA_ALCOOLICA', true, true, NOW(), NOW()),
('ALC002', 'Caipirinha', 'Vodka, limão, açúcar', 18.90, 'BEBIDA_ALCOOLICA', true, true, NOW(), NOW()),
('ALC003', 'Vinho Tinto Taça', 'Vinho tinto selecionado (150ml)', 22.90, 'BEBIDA_ALCOOLICA', true, true, NOW(), NOW()),
('ALC004', 'Chopp Pilsen', 'Chopp pilsen gelado (300ml)', 9.90, 'BEBIDA_ALCOOLICA', true, true, NOW(), NOW());

-- ========================================
-- DADOS INICIAIS - ATENDENTES
-- ========================================

-- NOTA: As senhas devem ser hasheadas na aplicação real
-- Senha exemplo: "senha123" (implementar hash BCrypt na aplicação)

INSERT INTO atendentes (nome, email, telefone, senha, tipo_usuario, ativo, created_at, updated_at)
VALUES 
('João Silva', 'joao.silva@restaurante.com', '+5511999998888', '$2a$10$examplehash', 'ATENDENTE', true, NOW(), NOW()),
('Maria Santos', 'maria.santos@restaurante.com', '+5511999997777', '$2a$10$examplehash', 'ATENDENTE', true, NOW(), NOW()),
('Carlos Oliveira', 'carlos.oliveira@restaurante.com', '+5511999996666', '$2a$10$examplehash', 'GERENTE', true, NOW(), NOW());

-- ========================================
-- OBSERVAÇÕES
-- ========================================

/*
IMPORTANTE:
1. Este script popula apenas dados iniciais para teste
2. As senhas dos atendentes devem ser hasheadas usando BCrypt
3. Os clientes são criados automaticamente quando fazem login via OTP
4. As mesas são criadas dinamicamente quando clientes escaneiam QR Code ou atendentes criam manualmente
5. Ajuste os preços e produtos conforme necessário para seu restaurante

PRÓXIMOS PASSOS:
- Implementar geração de QR Codes para as mesas físicas
- Configurar serviço de SMS/WhatsApp para envio de OTP
- Integrar com gateway de pagamento
- Implementar sistema de backup automático

COMANDOS ÚTEIS:
-- Ver todos os produtos disponíveis
SELECT codigo, nome, preco, categoria FROM produtos WHERE disponivel = true AND ativo = true ORDER BY categoria, nome;

-- Ver unidades de consumo abertas
SELECT uc.id, uc.referencia, uc.tipo, uc.status, c.telefone, uc.aberta_em 
FROM unidades_consumo uc 
INNER JOIN clientes c ON uc.cliente_id = c.id 
WHERE uc.status != 'FINALIZADA' 
ORDER BY uc.referencia;

-- Ver pedidos ativos
SELECT p.numero, p.status, uc.referencia as unidade_consumo, p.total, p.created_at 
FROM pedidos p 
INNER JOIN mesas m ON p.mesa_id = m.id 
WHERE p.status IN ('PENDENTE', 'RECEBIDO', 'EM_PREPARO') 
ORDER BY p.created_at;
*/
