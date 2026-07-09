package com.restaurante.delivery.service;

import com.restaurante.consumo.identificacao.service.TelefoneNormalizerService;
import com.restaurante.delivery.dto.request.CourierLocationRequest;
import com.restaurante.delivery.dto.request.CourierRegisterRequest;
import com.restaurante.delivery.dto.response.CourierProfileResponse;
import com.restaurante.delivery.repository.CourierLocationPingRepository;
import com.restaurante.delivery.repository.CourierProfileRepository;
import com.restaurante.delivery.util.DeliveryMasks;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.CourierLocationPing;
import com.restaurante.model.entity.CourierProfile;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.CourierAvailability;
import com.restaurante.model.enums.CourierLocationSource;
import com.restaurante.model.enums.CourierStatus;
import com.restaurante.model.enums.CourierVerificationStatus;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.Role;
import com.restaurante.repository.UserRepository;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CourierProfileService {

    private final CourierProfileRepository repository;
    private final CourierLocationPingRepository pingRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TelefoneNormalizerService telefoneNormalizerService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public CourierProfileResponse register(CourierRegisterRequest req) {
        if (req == null) throw new BusinessException("DELIVERY_COURIER_NOT_FOUND");
        if (req.getUsername() == null || req.getUsername().isBlank()) throw new BusinessException("DELIVERY_COURIER_NOT_FOUND");
        if (req.getPassword() == null || req.getPassword().isBlank()) throw new BusinessException("DELIVERY_COURIER_NOT_FOUND");
        if (req.getFullName() == null || req.getFullName().isBlank()) throw new BusinessException("DELIVERY_COURIER_NOT_FOUND");

        String normalizedPhone = req.getPhone() != null && !req.getPhone().isBlank()
                ? telefoneNormalizerService.normalizeOrThrow(req.getPhone())
                : null;
        String maskedPhone = normalizedPhone != null ? telefoneNormalizerService.mask(normalizedPhone) : null;

        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            throw new BusinessException("DELIVERY_COURIER_NOT_FOUND");
        }

        User u = new User();
        u.setUsername(req.getUsername());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setNomeCompleto(req.getFullName());
        u.setTelefone(normalizedPhone != null ? normalizedPhone : "+244900000000");
        u.setRoles(Set.of(Role.ROLE_COURIER));
        u.setAtivo(true);
        u = userRepository.save(u);

        CourierProfile c = new CourierProfile();
        c.setCourierUser(u);
        c.setCourierCode("CR-" + Long.toString(System.nanoTime(), 36).toUpperCase());
        c.setFullName(req.getFullName());
        c.setPhoneMasked(maskedPhone);
        c.setStatus(CourierStatus.ACTIVE);
        c.setVerificationStatus(CourierVerificationStatus.PENDING);
        c.setVehicleType(req.getVehicleType() != null ? req.getVehicleType() : c.getVehicleType());
        c.setVehiclePlateMasked(DeliveryMasks.maskPlate(req.getVehiclePlate()));
        c.setHasOwnVehicle(true);
        c.setAcceptsTerms(req.isAcceptsTerms());
        c.setCurrentAvailability(CourierAvailability.OFFLINE);
        c = repository.save(c);

        operationalEventLogService.logGenericForTenant(
                // couriers are cross-tenant; use tenantId=0 is not allowed, so we skip tenant-bound log here.
                // Instead, we log under SYSTEM tenant context when available; in MVP we keep a tenant log only when tenantId exists.
                // Caller flows will produce tenant-bound logs for delivery events.
                null,
                OperationalEventType.COURIER_REGISTERED,
                OperationalEntityType.COURIER_PROFILE,
                c.getId(),
                OperationalOrigem.SYSTEM,
                "Courier registrado",
                Map.of("courierId", c.getId(), "courierCode", c.getCourierCode(), "telefoneMascarado", maskedPhone),
                null,
                null
        );

        return map(c);
    }

    @Transactional(readOnly = true)
    public CourierProfile requireByUserId(Long userId) {
        if (userId == null) throw new BusinessException("DELIVERY_COURIER_NOT_FOUND");
        return repository.findByCourierUser_Id(userId).orElseThrow(() -> new BusinessException("DELIVERY_COURIER_NOT_FOUND"));
    }

    @Transactional
    public CourierProfileResponse setAvailability(Long courierUserId, CourierAvailability availability) {
        CourierProfile c = requireByUserId(courierUserId);
        if (c.getStatus() != CourierStatus.ACTIVE) throw new BusinessException("DELIVERY_COURIER_NOT_AVAILABLE");
        c.setCurrentAvailability(availability != null ? availability : CourierAvailability.OFFLINE);
        c = repository.save(c);
        return map(c);
    }

    @Transactional
    public CourierProfileResponse updateLocation(Long courierUserId, CourierLocationRequest req, CourierLocationSource source) {
        CourierProfile c = requireByUserId(courierUserId);
        if (req == null || req.getLatitude() == null || req.getLongitude() == null) throw new BusinessException("DELIVERY_COURIER_NOT_AVAILABLE");

        c.setCurrentLatitude(req.getLatitude());
        c.setCurrentLongitude(req.getLongitude());
        c.setLastLocationUpdateAt(LocalDateTime.now());
        c = repository.save(c);

        CourierLocationPing ping = new CourierLocationPing();
        ping.setCourier(c);
        ping.setLatitude(req.getLatitude());
        ping.setLongitude(req.getLongitude());
        ping.setAccuracyMeters(req.getAccuracyMeters());
        ping.setRecordedAt(LocalDateTime.now());
        ping.setSource(source != null ? source : CourierLocationSource.COURIER_APP);
        pingRepository.save(ping);

        return map(c);
    }

    @Transactional
    public CourierProfile markVerified(Long courierId) {
        CourierProfile c = repository.findById(courierId).orElseThrow(() -> new BusinessException("DELIVERY_COURIER_NOT_FOUND"));
        c.setVerificationStatus(CourierVerificationStatus.VERIFIED);
        return repository.save(c);
    }

    private static CourierProfileResponse map(CourierProfile c) {
        return new CourierProfileResponse(
                c.getId(),
                c.getCourierCode(),
                c.getFullName(),
                c.getPhoneMasked(),
                c.getStatus(),
                c.getVerificationStatus(),
                c.getVehicleType(),
                c.getVehiclePlateMasked(),
                c.isHasOwnVehicle(),
                c.isAcceptsTerms(),
                c.getCurrentAvailability(),
                c.getCurrentLatitude(),
                c.getCurrentLongitude(),
                c.getLastLocationUpdateAt(),
                c.getActiveDeliveryJobId()
        );
    }
}

