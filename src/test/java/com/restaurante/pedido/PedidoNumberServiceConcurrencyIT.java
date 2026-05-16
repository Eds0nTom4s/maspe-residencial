package com.restaurante.pedido;

import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.TenantRepository;
import com.restaurante.service.PedidoNumberService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it-postgres")
class PedidoNumberServiceConcurrencyIT extends PostgresTestcontainersConfig {

    @Autowired TenantRepository tenantRepository;
    @Autowired PedidoNumberService pedidoNumberService;

    @Test
    void generatesUniqueNumbersConcurrently_forSameTenant() throws Exception {
        Tenant tenant = criarTenant("Tenant Seq", "tenant-seq", "SEQ");

        int threads = 10;
        var pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<String>> tasks = java.util.stream.IntStream.range(0, threads)
                    .<Callable<String>>mapToObj(i -> () -> pedidoNumberService.gerarNumeroPedido(tenant.getId()))
                    .toList();
            List<Future<String>> futures = pool.invokeAll(tasks);

            Set<String> numeros = new HashSet<>();
            for (Future<String> f : futures) {
                numeros.add(f.get());
            }
            assertThat(numeros).hasSize(threads);
            assertThat(numeros.iterator().next()).contains("PED-SEQ-");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sequencesAreIndependentBetweenTenants() {
        Tenant a = criarTenant("Tenant A Seq", "tenant-a-seq", "A");
        Tenant b = criarTenant("Tenant B Seq", "tenant-b-seq", "B");

        String n1 = pedidoNumberService.gerarNumeroPedido(a.getId());
        String n2 = pedidoNumberService.gerarNumeroPedido(b.getId());

        assertThat(n1).contains("PED-A-");
        assertThat(n2).contains("PED-B-");
    }

    private Tenant criarTenant(String nome, String slug, String tenantCode) {
        Tenant t = new Tenant();
        t.setNome(nome);
        t.setSlug(slug);
        t.setTenantCode(tenantCode);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}

