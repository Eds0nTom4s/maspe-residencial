-- Prompt 37.7: Hardening WORM (trigger PostgreSQL anti-UPDATE/DELETE) para evidence bundles persistidos

-- Observação:
-- - Este hardening protege contra mutações acidentais ou SQL direto usando o mesmo usuário "normal" da aplicação.
-- - Um superuser do PostgreSQL pode burlar triggers. Essa limitação é intencional e será documentada.

create or replace function prevent_turno_evidence_bundle_mutation()
returns trigger
language plpgsql
as $$
begin
    if (tg_op = 'DELETE') then
        raise exception 'turno_evidence_bundles is WORM protected: DELETE is not allowed';
    end if;

    if (tg_op = 'UPDATE') then
        -- Regra central: só permitir UPDATE quando há transição válida de status.
        if (new.status is distinct from old.status) then
            -- Validar transições permitidas
            if not (
                (old.status = 'ACTIVE' and new.status in ('RETENTION_EXPIRED', 'QUARANTINED'))
                or
                (old.status = 'SUPERSEDED' and new.status in ('RETENTION_EXPIRED', 'QUARANTINED'))
            ) then
                raise exception 'turno_evidence_bundles is WORM protected: invalid status transition';
            end if;

            -- Campos imutáveis: bloquear qualquer alteração fora de status/updated_at/modified_by/version.
            if old.id is distinct from new.id
               or old.created_at is distinct from new.created_at
               or old.created_by is distinct from new.created_by
               or old.tenant_id is distinct from new.tenant_id
               or old.turno_id is distinct from new.turno_id
               or old.instituicao_id is distinct from new.instituicao_id
               or old.unidade_atendimento_id is distinct from new.unidade_atendimento_id
               or old.bundle_version is distinct from new.bundle_version
               or old.bundle_type is distinct from new.bundle_type
               or old.sequence_number is distinct from new.sequence_number
               or old.generated_at is distinct from new.generated_at
               or old.generated_by_user_id is distinct from new.generated_by_user_id
               or old.generated_by_actor_type is distinct from new.generated_by_actor_type
               or old.source_endpoint is distinct from new.source_endpoint
               or old.canonicalization_version is distinct from new.canonicalization_version
               or old.hash_algorithm is distinct from new.hash_algorithm
               or old.bundle_hash is distinct from new.bundle_hash
               or old.signature_algorithm is distinct from new.signature_algorithm
               or old.bundle_signature is distinct from new.bundle_signature
               or old.signature_key_id is distinct from new.signature_key_id
               or old.signature_generated_at is distinct from new.signature_generated_at
               or old.previous_bundle_id is distinct from new.previous_bundle_id
               or old.previous_bundle_hash is distinct from new.previous_bundle_hash
               or old.chain_hash is distinct from new.chain_hash
               or old.chain_signature is distinct from new.chain_signature
               or old.chain_signature_key_id is distinct from new.chain_signature_key_id
               or old.chain_signature_generated_at is distinct from new.chain_signature_generated_at
               or old.retention_until is distinct from new.retention_until
               or old.worm_locked is distinct from new.worm_locked
               or old.bundle_json is distinct from new.bundle_json
               or old.metadata_json is distinct from new.metadata_json
            then
                raise exception 'turno_evidence_bundles is WORM protected: immutable fields cannot be updated';
            end if;

            return new;
        else
            -- Se status não mudou, nenhum UPDATE é permitido (incluindo updated_at/version).
            raise exception 'turno_evidence_bundles is WORM protected: immutable fields cannot be updated';
        end if;
    end if;

    return new;
end;
$$;

drop trigger if exists trg_prevent_turno_evidence_bundle_mutation on turno_evidence_bundles;

create trigger trg_prevent_turno_evidence_bundle_mutation
before update or delete on turno_evidence_bundles
for each row
execute function prevent_turno_evidence_bundle_mutation();
