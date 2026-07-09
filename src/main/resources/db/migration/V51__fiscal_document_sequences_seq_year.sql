-- Prompt 43.1 hardening: evitar coluna reservada ("year") e manter compatibilidade PostgreSQL/H2
-- Renomeia fiscal_document_sequences.year -> seq_year e recria índice único.

do $$
begin
    if exists (
        select 1
        from information_schema.columns
        where table_name = 'fiscal_document_sequences'
          and column_name = 'year'
    ) then
        -- índice antigo
        begin
            execute 'drop index if exists uq_fiscal_seq_key';
        exception when others then
            -- noop
        end;

        execute 'alter table fiscal_document_sequences rename column year to seq_year';

        execute 'create unique index if not exists uq_fiscal_seq_key ' ||
                'on fiscal_document_sequences (tenant_id, unidade_atendimento_id, document_type, series, seq_year)';
    end if;
end $$;

