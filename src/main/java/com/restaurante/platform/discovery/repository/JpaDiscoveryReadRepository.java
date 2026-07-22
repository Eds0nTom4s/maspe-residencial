package com.restaurante.platform.discovery.repository;

import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.platform.discovery.repository.projection.MerchantDiscoveryProjection;
import com.restaurante.platform.discovery.repository.projection.MerchantLocationProjection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data adapter dedicated to the public Discovery read model. */
public interface JpaDiscoveryReadRepository extends Repository<Tenant, Long> {

    @Query(
            value = """
                    select t.slug as merchantId,
                           t.nome as name,
                           t.tipo as tenantType,
                           t.templateCode as templateCode,
                           cardapio.urlBanner as bannerUrl,
                           fiscal.province as province,
                           fiscal.municipality as municipality,
                           delivery.deliveryEnabled as deliveryEnabled,
                           delivery.allowCustomerPickup as customerPickupAllowed,
                           delivery.status as deliveryPolicyStatus,
                           operacao.allowPickup as operationPickupAllowed,
                           delivery.preparationTimeMinutes as preparationTimeMinutes,
                           cardapio.cardapioPublicado as catalogPublished,
                           (select count(product.id)
                            from Produto product
                            where product.tenant = t
                              and product.ativo = true
                              and product.disponivel = true
                              and product.categoriaProduto.ativo = true) as activeCatalogItemCount
                    from Tenant t
                    join t.businessAccount account
                    join TenantCardapioConfig cardapio on cardapio.tenant = t
                    left join TenantFiscalProfile fiscal on fiscal.tenant = t
                    left join TenantDeliveryPolicy delivery on delivery.tenant = t
                    left join TenantOperacaoPolicy operacao on operacao.tenant = t
                    where t.estado = :tenantState
                      and account.estado = :accountState
                      and trim(t.nome) <> ''
                      and trim(t.slug) <> ''
                      and (:publishedCatalogRequired = false or cardapio.cardapioPublicado = true)
                      and (:activeLocationRequired = false or exists (
                            select unit.id
                            from UnidadeAtendimento unit
                            where unit.instituicao.tenant = t
                              and unit.instituicao.ativa = true
                              and unit.ativa = true
                      ))
                      and (:query = ''
                           or lower(t.nome) like lower(concat('%', :query, '%')) escape '!'
                           or lower(t.slug) like lower(concat('%', :query, '%')) escape '!')
                      and t.tipo in :tenantTypes
                      and (:municipalityId is null
                           or lower(fiscal.municipality) = lower(:municipalityId))
                    """,
            countQuery = """
                    select count(t.id)
                    from Tenant t
                    join t.businessAccount account
                    join TenantCardapioConfig cardapio on cardapio.tenant = t
                    left join TenantFiscalProfile fiscal on fiscal.tenant = t
                    where t.estado = :tenantState
                      and account.estado = :accountState
                      and trim(t.nome) <> ''
                      and trim(t.slug) <> ''
                      and (:publishedCatalogRequired = false or cardapio.cardapioPublicado = true)
                      and (:activeLocationRequired = false or exists (
                            select unit.id
                            from UnidadeAtendimento unit
                            where unit.instituicao.tenant = t
                              and unit.instituicao.ativa = true
                              and unit.ativa = true
                      ))
                      and (:query = ''
                           or lower(t.nome) like lower(concat('%', :query, '%')) escape '!'
                           or lower(t.slug) like lower(concat('%', :query, '%')) escape '!')
                      and t.tipo in :tenantTypes
                      and (:municipalityId is null
                           or lower(fiscal.municipality) = lower(:municipalityId))
                    """)
    Page<MerchantDiscoveryProjection> findPublicMerchants(
            @Param("tenantState") TenantEstado tenantState,
            @Param("accountState") BusinessAccountEstado accountState,
            @Param("publishedCatalogRequired") boolean publishedCatalogRequired,
            @Param("activeLocationRequired") boolean activeLocationRequired,
            @Param("query") String query,
            @Param("tenantTypes") Collection<TenantTipo> tenantTypes,
            @Param("municipalityId") String municipalityId,
            Pageable pageable);

    @Query("""
            select t.slug as merchantId,
                   t.nome as name,
                   t.tipo as tenantType,
                   t.templateCode as templateCode,
                   cardapio.urlBanner as bannerUrl,
                   fiscal.province as province,
                   fiscal.municipality as municipality,
                   delivery.deliveryEnabled as deliveryEnabled,
                   delivery.allowCustomerPickup as customerPickupAllowed,
                   delivery.status as deliveryPolicyStatus,
                   operacao.allowPickup as operationPickupAllowed,
                   delivery.preparationTimeMinutes as preparationTimeMinutes,
                   cardapio.cardapioPublicado as catalogPublished,
                   (select count(product.id)
                    from Produto product
                    where product.tenant = t
                      and product.ativo = true
                      and product.disponivel = true
                      and product.categoriaProduto.ativo = true) as activeCatalogItemCount
            from Tenant t
            join t.businessAccount account
            join TenantCardapioConfig cardapio on cardapio.tenant = t
            left join TenantFiscalProfile fiscal on fiscal.tenant = t
            left join TenantDeliveryPolicy delivery on delivery.tenant = t
            left join TenantOperacaoPolicy operacao on operacao.tenant = t
            where t.slug = :merchantId
              and t.estado = :tenantState
              and account.estado = :accountState
              and trim(t.nome) <> ''
              and trim(t.slug) <> ''
              and (:publishedCatalogRequired = false or cardapio.cardapioPublicado = true)
              and (:activeLocationRequired = false or exists (
                    select unit.id
                    from UnidadeAtendimento unit
                    where unit.instituicao.tenant = t
                      and unit.instituicao.ativa = true
                      and unit.ativa = true
              ))
            """)
    Optional<MerchantDiscoveryProjection> findPublicMerchant(
            @Param("merchantId") String merchantId,
            @Param("tenantState") TenantEstado tenantState,
            @Param("accountState") BusinessAccountEstado accountState,
            @Param("publishedCatalogRequired") boolean publishedCatalogRequired,
            @Param("activeLocationRequired") boolean activeLocationRequired);

    @Query("""
            select institution.urlLogo as logoUrl,
                   unit.descricao as description,
                   unit.tipo as unitType
            from UnidadeAtendimento unit
            join unit.instituicao institution
            where institution.tenant.slug = :merchantId
              and institution.ativa = true
              and unit.ativa = true
            order by institution.id asc, unit.id asc
            """)
    List<MerchantLocationProjection> findPrimaryOperationalLocation(
            @Param("merchantId") String merchantId, Pageable pageable);
}
