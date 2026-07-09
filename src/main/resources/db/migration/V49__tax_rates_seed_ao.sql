-- Prompt 43: Seed idempotente de TaxRate (AO) - valores iniciais configuráveis
-- Date: 2026-05-24

insert into tax_rates (
    created_at,
    country_code, tax_type, code, name, rate, status, effective_from, effective_to, legal_reference
)
select
    now(),
    'AO', 'VAT', 'AO_VAT_STANDARD_14', 'IVA Padrão (14%)', 14.0000, 'ACTIVE', null, null, 'Seed inicial (configurável)'
where not exists (
    select 1 from tax_rates where country_code = 'AO' and code = 'AO_VAT_STANDARD_14'
);

insert into tax_rates (
    created_at,
    country_code, tax_type, code, name, rate, status, effective_from, effective_to, legal_reference
)
select
    now(),
    'AO', 'VAT', 'AO_VAT_SIMPLIFIED_7', 'IVA Simplificado (7%)', 7.0000, 'ACTIVE', null, null, 'Seed inicial (configurável)'
where not exists (
    select 1 from tax_rates where country_code = 'AO' and code = 'AO_VAT_SIMPLIFIED_7'
);

insert into tax_rates (
    created_at,
    country_code, tax_type, code, name, rate, status, effective_from, effective_to, legal_reference
)
select
    now(),
    'AO', 'VAT', 'AO_VAT_EXEMPT_0', 'IVA Isento (0%)', 0.0000, 'ACTIVE', null, null, 'Seed inicial (configurável)'
where not exists (
    select 1 from tax_rates where country_code = 'AO' and code = 'AO_VAT_EXEMPT_0'
);

insert into tax_rates (
    created_at,
    country_code, tax_type, code, name, rate, status, effective_from, effective_to, legal_reference
)
select
    now(),
    'AO', 'VAT', 'AO_VAT_ZERO_0', 'IVA Zero Rated (0%)', 0.0000, 'ACTIVE', null, null, 'Seed inicial (configurável)'
where not exists (
    select 1 from tax_rates where country_code = 'AO' and code = 'AO_VAT_ZERO_0'
);

