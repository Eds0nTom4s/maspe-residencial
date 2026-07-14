package com.restaurante.service.business;

import com.restaurante.model.entity.LegacyProvisioningUsageEvent;
import com.restaurante.repository.LegacyProvisioningUsageEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LegacyProvisioningUsageService {
    private final LegacyProvisioningUsageEventRepository repository;
    private final CanonicalCommandSupport commands;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String endpoint, HttpServletRequest request) {
        CanonicalCommandSupport.Actor actor = commands.actor(request);
        LegacyProvisioningUsageEvent event = new LegacyProvisioningUsageEvent();
        event.setEndpoint(endpoint);
        event.setActorUserId(actor.userId());
        event.setActorRoles(actor.roles());
        event.setCorrelationId(actor.correlationId());
        event.setIpAddress(actor.ip());
        event.setUserAgent(actor.userAgent());
        repository.saveAndFlush(event);
    }
}
