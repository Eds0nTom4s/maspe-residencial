package com.restaurante.repository;

import com.restaurante.model.entity.UnidadeDeConsumo;
import com.restaurante.model.enums.StatusUnidadeConsumo;
import com.restaurante.model.enums.TipoUnidadeConsumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnidadeDeConsumoRepository extends JpaRepository<UnidadeDeConsumo, Long> {

    /**
     * Busca unidade por QR Code
     */
    Optional<UnidadeDeConsumo> findByQrCode(String qrCode);

    /**
     * Busca unidades por status
     */
    List<UnidadeDeConsumo> findByStatus(StatusUnidadeConsumo status);

    /**
     * Busca unidades disponíveis de um tipo específico
     */
    List<UnidadeDeConsumo> findByStatusAndTipo(StatusUnidadeConsumo status, TipoUnidadeConsumo tipo);

    /**
     * Busca unidades de uma Unidade de Atendimento
     */
    @Query("SELECT uc FROM UnidadeDeConsumo uc WHERE uc.unidadeAtendimento.id = :unidadeAtendimentoId")
    List<UnidadeDeConsumo> findByUnidadeAtendimentoId(@Param("unidadeAtendimentoId") Long unidadeAtendimentoId);

    /**
     * Busca unidades ocupadas de uma Unidade de Atendimento
     */
    @Query("SELECT uc FROM UnidadeDeConsumo uc WHERE uc.unidadeAtendimento.id = :unidadeAtendimentoId AND uc.status = 'OCUPADA'")
    List<UnidadeDeConsumo> findUnidadesOcupadasByUnidadeAtendimento(@Param("unidadeAtendimentoId") Long unidadeAtendimentoId);

    /**
     * Busca unidades de um cliente
     */
    @Query("SELECT uc FROM UnidadeDeConsumo uc WHERE uc.cliente.id = :clienteId")
    List<UnidadeDeConsumo> findByClienteId(@Param("clienteId") Long clienteId);

    /**
     * Busca unidade ativa de um cliente (OCUPADA ou AGUARDANDO_PAGAMENTO)
     */
    @Query("SELECT uc FROM UnidadeDeConsumo uc WHERE uc.cliente.id = :clienteId AND uc.status IN ('OCUPADA', 'AGUARDANDO_PAGAMENTO')")
    Optional<UnidadeDeConsumo> findUnidadeAtivaByCliente(@Param("clienteId") Long clienteId);

    /**
     * Conta unidades por status
     */
    long countByStatus(StatusUnidadeConsumo status);

    /**
     * Conta unidades ocupadas de uma Unidade de Atendimento
     */
    @Query("SELECT COUNT(uc) FROM UnidadeDeConsumo uc WHERE uc.unidadeAtendimento.id = :unidadeAtendimentoId AND uc.status = 'OCUPADA'")
    long contarUnidadesOcupadasPorUnidadeAtendimento(@Param("unidadeAtendimentoId") Long unidadeAtendimentoId);
}
