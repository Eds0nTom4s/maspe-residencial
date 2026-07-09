-- Prompt 44.1 — Devoluções/estornos e impacto no stock/COGS (movimentos reversos append-only)

ALTER TABLE IF EXISTS tenant_inventory_policies
    ADD COLUMN IF NOT EXISTS allow_returns BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE IF EXISTS tenant_inventory_policies
    ADD COLUMN IF NOT EXISTS require_return_approval BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE IF EXISTS tenant_inventory_policies
    ADD COLUMN IF NOT EXISTS default_restock_policy VARCHAR(40) NOT NULL DEFAULT 'MANUAL_REVIEW';

ALTER TABLE IF EXISTS tenant_inventory_policies
    ADD COLUMN IF NOT EXISTS allow_partial_returns BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE IF EXISTS tenant_inventory_policies
    ADD COLUMN IF NOT EXISTS allow_return_after_turno_closed BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE IF EXISTS tenant_inventory_policies
    ADD COLUMN IF NOT EXISTS max_return_days INTEGER NOT NULL DEFAULT 30;

ALTER TABLE IF EXISTS tenant_inventory_policies
    ADD COLUMN IF NOT EXISTS require_fiscal_credit_note_for_return BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE IF EXISTS tenant_inventory_policies
    ADD COLUMN IF NOT EXISTS auto_process_return_on_credit_note BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS inventory_return_records (
    id BIGSERIAL PRIMARY KEY,

    tenant_id BIGINT NOT NULL,
    unidade_atendimento_id BIGINT NULL,
    pedido_id BIGINT NOT NULL,
    pagamento_id BIGINT NULL,
    fiscal_document_id BIGINT NULL,
    fiscal_correction_document_id BIGINT NULL,
    inventory_consumption_record_id BIGINT NOT NULL,

    return_type VARCHAR(60) NOT NULL,
    status VARCHAR(40) NOT NULL,
    source VARCHAR(40) NOT NULL,
    reason_category VARCHAR(80) NULL,
    reason_description TEXT NULL,

    requested_by_user_id BIGINT NULL,
    approved_by_user_id BIGINT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NULL,
    approved_at TIMESTAMP WITH TIME ZONE NULL,
    processed_at TIMESTAMP WITH TIME ZONE NULL,

    total_return_cost NUMERIC(19, 2) NULL,
    total_revenue_reversed NUMERIC(19, 2) NULL,
    total_tax_reversed NUMERIC(19, 2) NULL,
    total_margin_reversed NUMERIC(19, 2) NULL,
    warning_count INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL
);

ALTER TABLE IF EXISTS inventory_return_records
    ADD CONSTRAINT fk_inv_return_record_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE IF EXISTS inventory_return_records
    ADD CONSTRAINT fk_inv_return_record_unidade
        FOREIGN KEY (unidade_atendimento_id) REFERENCES unidades_atendimento(id);

ALTER TABLE IF EXISTS inventory_return_records
    ADD CONSTRAINT fk_inv_return_record_pedido
        FOREIGN KEY (pedido_id) REFERENCES pedidos(id);

ALTER TABLE IF EXISTS inventory_return_records
    ADD CONSTRAINT fk_inv_return_record_pagamento
        FOREIGN KEY (pagamento_id) REFERENCES pagamentos_gateway(id);

ALTER TABLE IF EXISTS inventory_return_records
    ADD CONSTRAINT fk_inv_return_record_fiscal_doc
        FOREIGN KEY (fiscal_document_id) REFERENCES fiscal_documents(id);

ALTER TABLE IF EXISTS inventory_return_records
    ADD CONSTRAINT fk_inv_return_record_fiscal_correction_doc
        FOREIGN KEY (fiscal_correction_document_id) REFERENCES fiscal_documents(id);

ALTER TABLE IF EXISTS inventory_return_records
    ADD CONSTRAINT fk_inv_return_record_consumption
        FOREIGN KEY (inventory_consumption_record_id) REFERENCES inventory_consumption_records(id);

CREATE INDEX IF NOT EXISTS idx_inv_return_record_tenant_status
    ON inventory_return_records (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_inv_return_record_tenant_pedido
    ON inventory_return_records (tenant_id, pedido_id);

CREATE INDEX IF NOT EXISTS idx_inv_return_record_tenant_processed
    ON inventory_return_records (tenant_id, processed_at);

CREATE TABLE IF NOT EXISTS inventory_return_lines (
    id BIGSERIAL PRIMARY KEY,

    inventory_return_record_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    pedido_item_id BIGINT NULL,
    product_id BIGINT NULL,
    inventory_consumption_line_id BIGINT NULL,
    inventory_item_id BIGINT NOT NULL,
    recipe_id BIGINT NULL,

    quantity_returned NUMERIC(19, 6) NOT NULL,
    unit_id BIGINT NOT NULL,
    quantity_base_unit NUMERIC(19, 6) NOT NULL,
    unit_cost NUMERIC(19, 6) NOT NULL DEFAULT 0,
    total_cost_reversed NUMERIC(19, 6) NOT NULL DEFAULT 0,
    stock_before NUMERIC(19, 6) NULL,
    stock_after NUMERIC(19, 6) NULL,

    restock_policy VARCHAR(40) NOT NULL,
    movement_id BIGINT NULL,
    warning_code VARCHAR(120) NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NULL,
    modified_by VARCHAR(100) NULL
);

ALTER TABLE IF EXISTS inventory_return_lines
    ADD CONSTRAINT fk_inv_return_line_record
        FOREIGN KEY (inventory_return_record_id) REFERENCES inventory_return_records(id);

ALTER TABLE IF EXISTS inventory_return_lines
    ADD CONSTRAINT fk_inv_return_line_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE IF EXISTS inventory_return_lines
    ADD CONSTRAINT fk_inv_return_line_itempedido
        FOREIGN KEY (pedido_item_id) REFERENCES itens_pedido(id);

ALTER TABLE IF EXISTS inventory_return_lines
    ADD CONSTRAINT fk_inv_return_line_product
        FOREIGN KEY (product_id) REFERENCES produtos(id);

ALTER TABLE IF EXISTS inventory_return_lines
    ADD CONSTRAINT fk_inv_return_line_consumption_line
        FOREIGN KEY (inventory_consumption_line_id) REFERENCES inventory_consumption_lines(id);

ALTER TABLE IF EXISTS inventory_return_lines
    ADD CONSTRAINT fk_inv_return_line_item
        FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id);

ALTER TABLE IF EXISTS inventory_return_lines
    ADD CONSTRAINT fk_inv_return_line_recipe
        FOREIGN KEY (recipe_id) REFERENCES inventory_recipes(id);

ALTER TABLE IF EXISTS inventory_return_lines
    ADD CONSTRAINT fk_inv_return_line_unit
        FOREIGN KEY (unit_id) REFERENCES unit_of_measure(id);

ALTER TABLE IF EXISTS inventory_return_lines
    ADD CONSTRAINT fk_inv_return_line_movement
        FOREIGN KEY (movement_id) REFERENCES inventory_movements(id);

ALTER TABLE IF EXISTS inventory_return_lines
    ADD CONSTRAINT chk_inv_return_line_qty
        CHECK (quantity_returned > 0 AND quantity_base_unit > 0);

ALTER TABLE IF EXISTS inventory_return_lines
    ADD CONSTRAINT chk_inv_return_line_costs
        CHECK (unit_cost >= 0 AND total_cost_reversed >= 0);

CREATE INDEX IF NOT EXISTS idx_inv_return_lines_record
    ON inventory_return_lines (inventory_return_record_id);

CREATE INDEX IF NOT EXISTS idx_inv_return_lines_tenant_item
    ON inventory_return_lines (tenant_id, inventory_item_id);

CREATE INDEX IF NOT EXISTS idx_inv_return_lines_tenant_pedido_item
    ON inventory_return_lines (tenant_id, pedido_item_id);

