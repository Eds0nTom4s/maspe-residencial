-- Prompt 44.2 — Hardening de devoluções/refund/margem reversa

ALTER TABLE IF EXISTS inventory_return_records
    ADD COLUMN IF NOT EXISTS fiscal_credit_note_id BIGINT NULL;

ALTER TABLE IF EXISTS inventory_return_records
    ADD COLUMN IF NOT EXISTS refund_reference_id VARCHAR(120) NULL;

ALTER TABLE IF EXISTS inventory_return_records
    ADD COLUMN IF NOT EXISTS refund_event_id VARCHAR(120) NULL;

DO $$
BEGIN
    IF to_regclass('inventory_return_records') IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inv_return_record_credit_note') THEN
        ALTER TABLE inventory_return_records
            ADD CONSTRAINT fk_inv_return_record_credit_note
                FOREIGN KEY (fiscal_credit_note_id) REFERENCES fiscal_documents(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_inv_return_record_credit_note
    ON inventory_return_records (tenant_id, fiscal_credit_note_id);

CREATE INDEX IF NOT EXISTS idx_inv_return_record_refund_reference
    ON inventory_return_records (tenant_id, refund_reference_id);

ALTER TABLE IF EXISTS inventory_return_lines
    ADD COLUMN IF NOT EXISTS total_revenue_reversed NUMERIC(19, 2) NULL;

ALTER TABLE IF EXISTS inventory_return_lines
    ADD COLUMN IF NOT EXISTS total_tax_reversed NUMERIC(19, 2) NULL;

ALTER TABLE IF EXISTS inventory_return_lines
    ADD COLUMN IF NOT EXISTS total_margin_reversed NUMERIC(19, 2) NULL;

ALTER TABLE IF EXISTS inventory_return_lines
    ADD COLUMN IF NOT EXISTS waste_movement_id BIGINT NULL;

ALTER TABLE IF EXISTS inventory_return_lines
    ADD COLUMN IF NOT EXISTS cogs_reversal_movement_id BIGINT NULL;

DO $$
BEGIN
    IF to_regclass('inventory_return_lines') IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inv_return_line_waste_movement') THEN
        ALTER TABLE inventory_return_lines
            ADD CONSTRAINT fk_inv_return_line_waste_movement
                FOREIGN KEY (waste_movement_id) REFERENCES inventory_movements(id);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('inventory_return_lines') IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inv_return_line_cogs_movement') THEN
        ALTER TABLE inventory_return_lines
            ADD CONSTRAINT fk_inv_return_line_cogs_movement
                FOREIGN KEY (cogs_reversal_movement_id) REFERENCES inventory_movements(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_inv_return_line_waste_movement
    ON inventory_return_lines (waste_movement_id);

CREATE INDEX IF NOT EXISTS idx_inv_return_line_cogs_movement
    ON inventory_return_lines (cogs_reversal_movement_id);
