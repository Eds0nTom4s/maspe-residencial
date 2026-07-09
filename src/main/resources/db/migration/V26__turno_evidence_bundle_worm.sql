-- Prompt 37.5/37.6: Persistência interna do Evidence Bundle + cadeia de custódia + retenção/WORM lógico

-- 1) Evidence bundles persistidos (append-only / WORM lógico)
create table if not exists turno_evidence_bundles (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    turno_id bigint not null,
    instituicao_id bigint,
    unidade_atendimento_id bigint,

    bundle_version varchar(30) not null,
    bundle_type varchar(50) not null,
    status varchar(30) not null,

    sequence_number integer not null,
    generated_at timestamp(6) not null,
    generated_by_user_id bigint,
    generated_by_actor_type varchar(50),
    source_endpoint varchar(150),

    canonicalization_version varchar(20) not null,
    hash_algorithm varchar(50) not null,
    bundle_hash varchar(128) not null,

    signature_algorithm varchar(50) not null,
    bundle_signature text not null,
    signature_key_id varchar(100) not null,
    signature_generated_at timestamp(6) not null,

    previous_bundle_id bigint,
    previous_bundle_hash varchar(128),

    chain_hash varchar(128) not null,
    chain_signature text not null,
    chain_signature_key_id varchar(100) not null,
    chain_signature_generated_at timestamp(6) not null,

    retention_until timestamp(6),
    worm_locked boolean not null default true,

    bundle_json jsonb not null,
    metadata_json jsonb,

    primary key (id),
    constraint fk_turno_ev_bundle_tenant foreign key (tenant_id) references tenants,
    constraint fk_turno_ev_bundle_turno foreign key (turno_id) references turnos_operacionais,
    constraint fk_turno_ev_bundle_inst foreign key (instituicao_id) references instituicoes,
    constraint fk_turno_ev_bundle_unidade foreign key (unidade_atendimento_id) references unidades_atendimento,
    constraint fk_turno_ev_bundle_prev foreign key (previous_bundle_id) references turno_evidence_bundles,
    constraint uk_turno_ev_bundle_seq unique (tenant_id, turno_id, sequence_number)
);

create index if not exists idx_turno_ev_bundle_turno on turno_evidence_bundles (tenant_id, turno_id);
create index if not exists idx_turno_ev_bundle_generated_at on turno_evidence_bundles (tenant_id, generated_at);
create index if not exists idx_turno_ev_bundle_hash on turno_evidence_bundles (tenant_id, bundle_hash);
create index if not exists idx_turno_ev_bundle_status on turno_evidence_bundles (tenant_id, status);

-- 2) Access log (cadeia de custódia de acessos/export/verificação)
create table if not exists turno_evidence_bundle_access_logs (
    id bigserial not null,
    version bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6),
    created_by varchar(100),
    modified_by varchar(100),

    tenant_id bigint not null,
    bundle_id bigint not null,
    turno_id bigint not null,

    accessed_at timestamp(6) not null,
    accessed_by_user_id bigint,
    actor_type varchar(50),
    access_type varchar(50) not null,
    source_ip varchar(100),
    user_agent varchar(255),
    verification_result varchar(50),

    metadata_json jsonb,

    primary key (id),
    constraint fk_turno_ev_access_tenant foreign key (tenant_id) references tenants,
    constraint fk_turno_ev_access_bundle foreign key (bundle_id) references turno_evidence_bundles,
    constraint fk_turno_ev_access_turno foreign key (turno_id) references turnos_operacionais
);

create index if not exists idx_turno_ev_access_bundle on turno_evidence_bundle_access_logs (tenant_id, bundle_id);
create index if not exists idx_turno_ev_access_turno on turno_evidence_bundle_access_logs (tenant_id, turno_id);
create index if not exists idx_turno_ev_access_at on turno_evidence_bundle_access_logs (tenant_id, accessed_at);

