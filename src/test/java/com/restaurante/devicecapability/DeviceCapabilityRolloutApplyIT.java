package com.restaurante.devicecapability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.device.capability.repository.DeviceOperationalCapabilityRepository;
import com.restaurante.dto.request.DeviceCapabilityRolloutRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.enums.*;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.repository.UserRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class DeviceCapabilityRolloutApplyIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired DeviceOperationalCapabilityRepository capabilityRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    private String tenantToken(ProvisionarTenantResponse prov) {
        var tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        var user = userRepository.findById(prov.getOwnerUserId()).orElseThrow();
        return jwtTokenProvider.generateTenantScopedToken(
                user,
                tenant,
                TenantUserRole.TENANT_OWNER,
                TenantUserEstado.ATIVO,
                1,
                null
        );
    }

    private UsernamePasswordAuthenticationToken tenantAuth(ProvisionarTenantResponse prov) {
        com.restaurante.security.JwtPrincipal principal = com.restaurante.security.JwtPrincipal.builder()
                .userId(prov.getOwnerUserId())
                .username(prov.getOwnerEmail())
                .email(prov.getOwnerEmail())
                .tokenType("TENANT")
                .tenantId(prov.getTenantId())
                .tenantCode(prov.getTenantCode())
                .tenantRoles(Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_GERENTE")))
                .build();
        return new UsernamePasswordAuthenticationToken(principal, "N/A", principal.getAuthorities());
    }

    @Test
    void apply_rollout_creates_template_managed_capabilities() throws Exception {
        final ProvisionarTenantResponse[] provArr = new ProvisionarTenantResponse[1];
        final DispositivoOperacional[] dArr = new DispositivoOperacional[1];

        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            provArr[0] = provisionTenant("cap-roll-a", "CRA");
            dArr[0] = criarDevice(provArr[0], OperationalDeviceType.POS_CAIXA);
        });

        ProvisionarTenantResponse prov = provArr[0];
        DispositivoOperacional d = dArr[0];

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        String token = tenantToken(prov);
        Long tpl = templateIdByCode("CAP_POS_CAIXA_PADRAO", prov);

        DeviceCapabilityRolloutRequest req = new DeviceCapabilityRolloutRequest();
        req.setUnidadeId(prov.getUnidadeAtendimentoId());
        req.setRolloutMode(DeviceCapabilityRolloutMode.UNIT_BY_DEVICE_TYPE);
        req.setTargetDeviceType(OperationalDeviceType.POS_CAIXA);
        req.setOverwriteMode(DeviceCapabilityOverwriteMode.OVERWRITE_EXISTING);

        mockMvc.perform(post("/tenant/device-capability-templates/{templateId}/rollout/apply", tpl)
                        .with(authentication(tenantAuth(prov)))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        var cap = capabilityRepository.findByTenant_IdAndDispositivoOperacional_IdAndCapability(prov.getTenantId(), d.getId(), DeviceCapability.LOOKUP_CONSUMPTION_BY_PHONE)
                .orElseThrow();
        assertThat(cap.isTemplateManaged()).isTrue();
        assertThat(cap.isManualOverride()).isFalse();
        assertThat(cap.getSourceTemplateId()).isEqualTo(tpl);
        assertThat(cap.getSourceRolloutId()).isNotNull();
        assertThat(cap.getTemplateAppliedAt()).isNotNull();
    }

    private Long templateIdByCode(String code, ProvisionarTenantResponse prov) throws Exception {
        String token = tenantToken(prov);
        String list = mockMvc.perform(get("/tenant/device-capability-templates")
                        .with(authentication(tenantAuth(prov)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode arr = objectMapper.readTree(list).at("/data");
        for (JsonNode n : arr) {
            if (code.equals(n.at("/code").asText())) return n.at("/templateId").asLong();
        }
        throw new IllegalStateException("Template não encontrado: " + code);
    }

    private DispositivoOperacional criarDevice(ProvisionarTenantResponse prov, OperationalDeviceType opType) {
        var tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        var inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        var ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setNome("Device Cap Roll");
        d.setCodigo("DCR-" + System.nanoTime());
        d.setTipo(DispositivoTipo.POS);
        d.setOperationalDeviceType(opType);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }

    private ProvisionarTenantResponse provisionTenant(String nome, String code) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false
        ));
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 100_000L));
        String uniqueCode = code + suffix;
        if (uniqueCode.length() > 10) uniqueCode = uniqueCode.substring(0, 10);
        String phone = "+244900" + Math.abs(nome.hashCode() % 1_000_000);
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + nome)
                                .slug(nome + "-" + suffix)
                                .tenantCode(uniqueCode)
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + nome)
                                .sigla(uniqueCode.substring(0, Math.min(4, uniqueCode.length())))
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(nome + suffix + "@owner.com")
                                .telefone(phone)
                                .criarUsuario(true)
                                .build())
                        .build()
        );
    }
}

