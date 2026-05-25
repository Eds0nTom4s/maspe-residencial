-- Prompt 44.2 — policy hardening de devoluções/refund

ALTER TABLE IF EXISTS tenant_inventory_policies
    ADD COLUMN IF NOT EXISTS auto_create_return_on_credit_note BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE IF EXISTS tenant_inventory_policies
    ADD COLUMN IF NOT EXISTS auto_create_return_on_refund BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE IF EXISTS tenant_inventory_policies
    ADD COLUMN IF NOT EXISTS auto_process_return_on_refund BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE IF EXISTS tenant_inventory_policies
    ADD COLUMN IF NOT EXISTS default_refund_restock_policy VARCHAR(40) NOT NULL DEFAULT 'MANUAL_REVIEW';

ALTER TABLE IF EXISTS tenant_inventory_policies
    ADD COLUMN IF NOT EXISTS require_credit_note_for_financial_return BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE IF EXISTS tenant_inventory_policies
    ADD COLUMN IF NOT EXISTS block_process_when_manual_review_line_exists BOOLEAN NOT NULL DEFAULT true;

