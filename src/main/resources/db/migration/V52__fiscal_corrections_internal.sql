-- Prompt 43.2 — Notas de crédito/débito internas e impacto fiscal de ajustes

-- 1) Avaliação fiscal de ajustes operacionais aprovados (caixa)
create table if not exists fiscal_adjustment_assessments (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    caixa_operador_adjustment_id bigint not null,
    caixa_operador_divergence_id bigint,
    caixa_operador_session_id bigint,
    turno_operacional_id bigint,
    unidade_atendimento_id bigint,

    original_fiscal_document_id bigint,

    status varchar(60) not null,
    impact_type varchar(60) not null,
    decision_reason text,

    assessed_by_user_id bigint,
    assessed_at timestamp(6),

    primary key (id),
    constraint fk_fiscal_assessment_tenant foreign key (tenant_id) references tenants,
    constraint fk_fiscal_assessment_adj foreign key (caixa_operador_adjustment_id) references caixa_operador_adjustments,
    constraint fk_fiscal_assessment_div foreign key (caixa_operador_divergence_id) references caixa_operador_divergences,
    constraint fk_fiscal_assessment_caixa foreign key (caixa_operador_session_id) references caixa_operador_sessions,
    constraint fk_fiscal_assessment_turno foreign key (turno_operacional_id) references turnos_operacionais,
    constraint fk_fiscal_assessment_unidade foreign key (unidade_atendimento_id) references unidades_atendimento,
    constraint fk_fiscal_assessment_original_doc foreign key (original_fiscal_document_id) references fiscal_documents,
    constraint fk_fiscal_assessment_assessed_by foreign key (assessed_by_user_id) references users
);

create unique index if not exists uq_fiscal_assessment_one_per_adjustment
    on fiscal_adjustment_assessments (tenant_id, caixa_operador_adjustment_id);

create index if not exists idx_fiscal_assessment_tenant_status
    on fiscal_adjustment_assessments (tenant_id, status);

create index if not exists idx_fiscal_assessment_tenant_created_at
    on fiscal_adjustment_assessments (tenant_id, created_at);

create index if not exists idx_fiscal_assessment_original_doc
    on fiscal_adjustment_assessments (tenant_id, original_fiscal_document_id);

-- 2) Política de correção fiscal por tenant (MVP defaults via ausência de registro)
create table if not exists tenant_fiscal_correction_policies (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    status varchar(30) not null,
    effective_from timestamp(6),
    effective_to timestamp(6),

    auto_assess_adjustments boolean not null default true,
    auto_issue_credit_note boolean not null default false,
    auto_issue_debit_note boolean not null default false,
    require_manual_review_for_loss boolean not null default true,
    require_manual_review_for_surplus boolean not null default true,
    allow_correction_without_original_document boolean not null default false,
    max_auto_correction_amount numeric(19,2) not null default 0,

    primary key (id),
    constraint fk_fiscal_correction_policy_tenant foreign key (tenant_id) references tenants
);

create unique index if not exists uq_tenant_fiscal_correction_policy_one_active
    on tenant_fiscal_correction_policies (tenant_id)
    where status = 'ACTIVE';

-- 3) Linkagem de documento fiscal corretivo interno ao documento original/assessment/adjustment
alter table if exists fiscal_documents add column if not exists original_fiscal_document_id bigint;
alter table if exists fiscal_documents add column if not exists correction_reason text;
alter table if exists fiscal_documents add column if not exists correction_source varchar(80);
alter table if exists fiscal_documents add column if not exists caixa_operador_adjustment_id bigint;
alter table if exists fiscal_documents add column if not exists fiscal_adjustment_assessment_id bigint;

do $$
begin
    if to_regclass('public.fiscal_documents') is not null
       and not exists (select 1 from pg_constraint where conname = 'fk_fiscal_doc_original_doc')
    then
        alter table fiscal_documents
            add constraint fk_fiscal_doc_original_doc
                foreign key (original_fiscal_document_id) references fiscal_documents;
    end if;
end
$$;

do $$
begin
    if to_regclass('public.fiscal_documents') is not null
       and not exists (select 1 from pg_constraint where conname = 'fk_fiscal_doc_caixa_adj')
    then
        alter table fiscal_documents
            add constraint fk_fiscal_doc_caixa_adj
                foreign key (caixa_operador_adjustment_id) references caixa_operador_adjustments;
    end if;
end
$$;

do $$
begin
    if to_regclass('public.fiscal_documents') is not null
       and not exists (select 1 from pg_constraint where conname = 'fk_fiscal_doc_assessment')
    then
        alter table fiscal_documents
            add constraint fk_fiscal_doc_assessment
                foreign key (fiscal_adjustment_assessment_id) references fiscal_adjustment_assessments;
    end if;
end
$$;

create index if not exists idx_fiscal_doc_original_doc
    on fiscal_documents (tenant_id, original_fiscal_document_id);

create index if not exists idx_fiscal_doc_caixa_adj
    on fiscal_documents (tenant_id, caixa_operador_adjustment_id);

create index if not exists idx_fiscal_doc_assessment
    on fiscal_documents (tenant_id, fiscal_adjustment_assessment_id);
