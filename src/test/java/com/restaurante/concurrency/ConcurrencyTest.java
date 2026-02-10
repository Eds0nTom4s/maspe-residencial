package com.restaurante.concurrency;

import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.enums.StatusMesa;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.PedidoRepository;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de concorrência para validar Optimistic Locking
 * 
 * Estes testes simulam cenários reais de race conditions
 * e validam que o sistema detecta e trata conflitos corretamente.
 * 
 * IMPORTANTE: Testes de concorrência podem ser não-determinísticos.
 * Execute múltiplas vezes para garantir consistência.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyTest {

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    /**
     * CENÁRIO: Dois threads tentam atualizar a mesma mesa simultaneamente
     * 
     * Setup:
     * 1. Mesa criada com status OCUPADA
     * 2. Thread A lê mesa (version=0)
     * 3. Thread B lê mesa (version=0)
     * 4. Thread A atualiza status (version=1) - SUCESSO
     * 5. Thread B tenta atualizar status (version=0) - FALHA
     * 
     * RESULTADO ESPERADO:
     * - Thread A: sucesso
     * - Thread B: OptimisticLockException
     * - Total de exceções: 1
     */
    @Test
    @DisplayName("Deve detectar conflito ao atualizar mesa concorrentemente")
    void testConcurrentMesaUpdate() throws InterruptedException {
        // Setup: Cria mesa de teste
        Mesa mesa = Mesa.builder()
                .numero(99)
                .status(StatusMesa.OCUPADA)
                .build();
        mesa = mesaRepository.save(mesa);
        final Long mesaId = mesa.getId();

        // Contador de exceções de concorrência
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // Sincronização: garante que threads iniciem ao mesmo tempo
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: Tenta mudar para AGUARDANDO_PAGAMENTO
        executor.submit(() -> {
            try {
                startLatch.await(); // Aguarda sinal para iniciar
                
                Mesa m = mesaRepository.findById(mesaId).orElseThrow();
                m.setStatus(StatusMesa.AGUARDANDO_PAGAMENTO);
                
                // Simula processamento para aumentar chance de race condition
                Thread.sleep(50);
                
                mesaRepository.save(m);
                successCount.incrementAndGet();
                
            } catch (OptimisticLockException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Tenta mudar para FINALIZADA
        executor.submit(() -> {
            try {
                startLatch.await(); // Aguarda sinal para iniciar
                
                Mesa m = mesaRepository.findById(mesaId).orElseThrow();
                m.setStatus(StatusMesa.FINALIZADA);
                
                // Simula processamento
                Thread.sleep(50);
                
                mesaRepository.save(m);
                successCount.incrementAndGet();
                
            } catch (OptimisticLockException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        // Inicia threads simultaneamente
        startLatch.countDown();

        // Aguarda conclusão
        doneLatch.await();
        executor.shutdown();

        // Validações
        System.out.println("Sucessos: " + successCount.get());
        System.out.println("Conflitos: " + conflictCount.get());

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        // Valida que apenas uma alteração foi persistida
        Mesa mesaFinal = mesaRepository.findById(mesaId).orElseThrow();
        assertThat(mesaFinal.getVersion()).isEqualTo(1L); // Version incrementada uma vez
        assertThat(mesaFinal.getStatus())
                .isIn(StatusMesa.AGUARDANDO_PAGAMENTO, StatusMesa.FINALIZADA);
    }

    /**
     * CENÁRIO: Múltiplos threads tentam atualizar o mesmo pedido
     * 
     * RESULTADO ESPERADO:
     * - Apenas 1 thread deve ter sucesso
     * - Demais threads devem receber OptimisticLockException
     */
    @Test
    @DisplayName("Deve detectar conflito ao atualizar pedido concorrentemente")
    void testConcurrentPedidoUpdate() throws InterruptedException {
        // Setup: Cria pedido de teste
        Pedido pedido = Pedido.builder()
                .numero("TEST-001")
                .status(StatusPedido.PENDENTE)
                .total(BigDecimal.ZERO)
                .build();
        pedido = pedidoRepository.save(pedido);
        final Long pedidoId = pedido.getId();

        int threadCount = 5;
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Cria múltiplos threads tentando atualizar
        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    Pedido p = pedidoRepository.findById(pedidoId).orElseThrow();
                    p.setObservacoes("Thread " + threadNum);
                    
                    Thread.sleep(50);
                    
                    pedidoRepository.save(p);
                    successCount.incrementAndGet();
                    
                } catch (OptimisticLockException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        System.out.println("Threads: " + threadCount);
        System.out.println("Sucessos: " + successCount.get());
        System.out.println("Conflitos: " + conflictCount.get());

        // Validações
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(conflictCount.get()).isGreaterThanOrEqualTo(threadCount - 2);
        assertThat(successCount.get() + conflictCount.get()).isEqualTo(threadCount);

        // Valida versão final
        Pedido pedidoFinal = pedidoRepository.findById(pedidoId).orElseThrow();
        assertThat(pedidoFinal.getVersion()).isGreaterThanOrEqualTo(1L);
    }

    /**
     * CENÁRIO: Validar que @Version funciona corretamente
     * 
     * Testa sequência de updates sem concorrência
     * para garantir que version é incrementada corretamente
     */
    @Test
    @DisplayName("Deve incrementar version a cada update")
    void testVersionIncrement() {
        // Cria entidade
        Mesa mesa = Mesa.builder()
                .numero(100)
                .status(StatusMesa.OCUPADA)
                .build();
        mesa = mesaRepository.save(mesa);
        
        assertThat(mesa.getVersion()).isEqualTo(0L);

        // Update 1
        mesa.setStatus(StatusMesa.AGUARDANDO_PAGAMENTO);
        mesa = mesaRepository.save(mesa);
        assertThat(mesa.getVersion()).isEqualTo(1L);

        // Update 2
        mesa.setStatus(StatusMesa.FINALIZADA);
        mesa = mesaRepository.save(mesa);
        assertThat(mesa.getVersion()).isEqualTo(2L);

        // Update 3
        mesa.setCapacidade(4);
        mesa = mesaRepository.save(mesa);
        assertThat(mesa.getVersion()).isEqualTo(3L);
    }

    /**
     * CENÁRIO: Validar que auditoria captura usuário
     * 
     * Testa se createdBy e modifiedBy são populados
     */
    @Test
    @DisplayName("Deve capturar usuário na auditoria")
    void testAuditFields() {
        // Cria entidade
        Pedido pedido = Pedido.builder()
                .numero("TEST-AUDIT-001")
                .status(StatusPedido.PENDENTE)
                .total(BigDecimal.ZERO)
                .build();
        
        pedido = pedidoRepository.save(pedido);

        // Valida campos de auditoria
        assertThat(pedido.getCreatedBy()).isNotNull();
        assertThat(pedido.getCreatedBy()).isEqualTo("system"); // ETAPA 02 usa "system"
        assertThat(pedido.getCreatedAt()).isNotNull();
        assertThat(pedido.getModifiedBy()).isNotNull();
        assertThat(pedido.getUpdatedAt()).isNotNull();

        // Update
        String originalCreatedBy = pedido.getCreatedBy();
        pedido.setObservacoes("Atualizado");
        pedido = pedidoRepository.save(pedido);

        // Valida que createdBy não muda, mas modifiedBy é atualizado
        assertThat(pedido.getCreatedBy()).isEqualTo(originalCreatedBy);
        assertThat(pedido.getModifiedBy()).isEqualTo("system");
    }
}
