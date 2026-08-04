ALTER TABLE fiscal_documents
    ADD COLUMN IF NOT EXISTS public_share_token_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS public_share_expires_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uq_fiscal_document_public_share_token
    ON fiscal_documents(public_share_token_hash)
    WHERE public_share_token_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_fiscal_document_public_share_expiry
    ON fiscal_documents(public_share_expires_at)
    WHERE public_share_token_hash IS NOT NULL;
