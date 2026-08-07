package com.restaurante.android.discovery.repository;

import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Persisted, publication-safe read model for the Android public Discovery. */
public interface AndroidDiscoveryReadRepository extends Repository<Tenant, Long> {

    @Query(
            value = """
                    select t.merchantPublicId as merchantId,
                           t.nome as name,
                           cardapio.cardapioPublicado as catalogPublished,
                           (select count(product.id)
                            from Produto product
                            where product.tenant = t
                              and product.ativo = true
                              and product.disponivel = true
                              and product.categoriaProduto.ativo = true) as activeCatalogItemCount
                    from Tenant t
                    join TenantCardapioConfig cardapio on cardapio.tenant = t
                    left join t.businessAccount account
                    where t.discoveryPublished = true
                      and t.estado = :tenantState
                      and cardapio.cardapioPublicado = true
                      and trim(t.nome) <> ''
                      and length(trim(t.nome)) <= 120
                      and (account is null or (
                            account.estado = :accountState
                            and exists (select subscription.id
                                        from Subscricao subscription
                                        where subscription.tenant = t
                                          and subscription.estado = :subscriptionState)))
                      and (:query = ''
                           or lower(t.nome) like lower(concat('%', :query, '%')) escape '!'
                           or lower(t.slug) like lower(concat('%', :query, '%')) escape '!')
                      and (:municipality is null or exists (
                            select fiscal.id
                            from TenantFiscalProfile fiscal
                            where fiscal.tenant = t
                              and lower(fiscal.municipality) = :municipality))
                    order by lower(t.nome) asc, t.merchantPublicId asc
                    """,
            countQuery = """
                    select count(t.id)
                    from Tenant t
                    join TenantCardapioConfig cardapio on cardapio.tenant = t
                    left join t.businessAccount account
                    where t.discoveryPublished = true
                      and t.estado = :tenantState
                      and cardapio.cardapioPublicado = true
                      and trim(t.nome) <> ''
                      and length(trim(t.nome)) <= 120
                      and (account is null or (
                            account.estado = :accountState
                            and exists (select subscription.id
                                        from Subscricao subscription
                                        where subscription.tenant = t
                                          and subscription.estado = :subscriptionState)))
                      and (:query = ''
                           or lower(t.nome) like lower(concat('%', :query, '%')) escape '!'
                           or lower(t.slug) like lower(concat('%', :query, '%')) escape '!')
                      and (:municipality is null or exists (
                            select fiscal.id
                            from TenantFiscalProfile fiscal
                            where fiscal.tenant = t
                              and lower(fiscal.municipality) = :municipality))
                    """)
    Page<AndroidDiscoveryMerchantProjection> findPublicMerchants(
            @Param("tenantState") TenantEstado tenantState,
            @Param("accountState") BusinessAccountEstado accountState,
            @Param("subscriptionState") SubscricaoEstado subscriptionState,
            @Param("query") String query,
            @Param("municipality") String municipality,
            Pageable pageable);

    @Query("""
            select t.merchantPublicId as merchantId,
                   t.nome as name,
                   cardapio.cardapioPublicado as catalogPublished,
                   (select count(product.id)
                    from Produto product
                    where product.tenant = t
                      and product.ativo = true
                      and product.disponivel = true
                      and product.categoriaProduto.ativo = true) as activeCatalogItemCount
            from Tenant t
            join TenantCardapioConfig cardapio on cardapio.tenant = t
            left join t.businessAccount account
            where t.merchantPublicId = :merchantPublicId
              and t.discoveryPublished = true
              and t.estado = :tenantState
              and cardapio.cardapioPublicado = true
              and trim(t.nome) <> ''
              and length(trim(t.nome)) <= 120
              and (account is null or (
                    account.estado = :accountState
                    and exists (select subscription.id
                                from Subscricao subscription
                                where subscription.tenant = t
                                  and subscription.estado = :subscriptionState)))
            """)
    Optional<AndroidDiscoveryMerchantProjection> findPublicMerchant(
            @Param("merchantPublicId") UUID merchantPublicId,
            @Param("tenantState") TenantEstado tenantState,
            @Param("accountState") BusinessAccountEstado accountState,
            @Param("subscriptionState") SubscricaoEstado subscriptionState);
}
