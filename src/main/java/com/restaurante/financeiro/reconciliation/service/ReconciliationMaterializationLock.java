package com.restaurante.financeiro.reconciliation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Serializa uma Idempotency-Key durante a transacção de materialização. */
@Component
@RequiredArgsConstructor
public class ReconciliationMaterializationLock {
    private final JdbcTemplate jdbc;

    public void acquire(String key) {
        jdbc.query("select pg_advisory_xact_lock(?)", ps -> ps.setLong(1, lockId(key)), rs -> null);
    }

    private long lockId(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível calcular o lock da Idempotency-Key.", e);
        }
    }
}
