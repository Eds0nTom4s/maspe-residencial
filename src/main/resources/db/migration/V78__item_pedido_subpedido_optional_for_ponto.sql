-- CONSUMA PONTO sem produção/KDS mantém itens diretamente no pedido.
-- Fluxos com produção continuam atribuindo sub_pedido_id pela camada de domínio.
ALTER TABLE itens_pedido ALTER COLUMN sub_pedido_id DROP NOT NULL;
