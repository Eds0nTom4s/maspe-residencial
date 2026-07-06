-- Formalização da origem operacional do pedido
-- Não destrutiva: coluna aditiva, preenchimento conservador para dados legados.
ALTER TABLE pedidos
    ADD COLUMN IF NOT EXISTS pedido_origem VARCHAR(30);

UPDATE pedidos
SET pedido_origem = 'LEGADO'
WHERE pedido_origem IS NULL;
