package com.restaurante.repository;

import com.restaurante.model.entity.EventoSessao;
import com.restaurante.model.enums.TipoEventoSessao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository para EventoSessao — entidade de auditoria append-only.
 *
 * <p>Permite salvar eventos individualmente sem precisar de cascade
 * pela SessaoConsumo, sendo mais eficiente para operações de escrita
 * isoladas (ex: expiração pelo scheduler).
 */
@Repository
public interface EventoSessaoRepository extends JpaRepository<EventoSessao, Long> {

    /**
     * Historial de eventos de uma sessão, por ordem cronológica.
     */
    List<EventoSessao> findBySessaoConsumoIdOrderByCreatedAtAsc(Long sessaoConsumoId);

    /**
     * Eventos de um tipo específico para uma sessão.
     */
    List<EventoSessao> findBySessaoConsumoIdAndTipoEvento(Long sessaoConsumoId, TipoEventoSessao tipoEvento);
}
