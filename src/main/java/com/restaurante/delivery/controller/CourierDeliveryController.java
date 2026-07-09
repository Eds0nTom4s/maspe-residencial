package com.restaurante.delivery.controller;

import com.restaurante.delivery.dto.request.CourierLocationRequest;
import com.restaurante.delivery.dto.request.CourierRegisterRequest;
import com.restaurante.delivery.dto.response.CourierProfileResponse;
import com.restaurante.delivery.dto.response.DeliveryInviteResponse;
import com.restaurante.delivery.dto.response.DeliveryJobResponse;
import com.restaurante.delivery.repository.DeliveryCourierInviteRepository;
import com.restaurante.delivery.repository.DeliveryJobRepository;
import com.restaurante.delivery.service.CourierProfileService;
import com.restaurante.delivery.service.DeliveryJobService;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.CourierProfile;
import com.restaurante.model.entity.DeliveryCourierInvite;
import com.restaurante.model.entity.DeliveryJob;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.CourierAvailability;
import com.restaurante.model.enums.CourierLocationSource;
import com.restaurante.model.enums.DeliveryCourierInviteStatus;
import com.restaurante.repository.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/courier")
@RequiredArgsConstructor
@Tag(name = "Courier - Logistica & Delivery", description = "Endpoints de uso exclusivo por entregadores parceiros")
public class CourierDeliveryController {

    private final UserRepository userRepository;
    private final CourierProfileService courierProfileService;
    private final DeliveryJobService deliveryJobService;
    private final DeliveryCourierInviteRepository inviteRepository;
    private final DeliveryJobRepository deliveryJobRepository;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<CourierProfileResponse>> register(@Valid @RequestBody CourierRegisterRequest request) {
        CourierProfileResponse resp = courierProfileService.register(request);
        return ResponseEntity.ok(ApiResponse.success("Entregador registrado com sucesso", resp));
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CourierProfileResponse>> getProfile() {
        User user = getCurrentUser();
        CourierProfile c = courierProfileService.requireByUserId(user.getId());
        return ResponseEntity.ok(ApiResponse.success("Perfil obtido", mapProfile(c)));
    }

    @PostMapping("/availability/online")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CourierProfileResponse>> setOnline() {
        User user = getCurrentUser();
        CourierProfileResponse resp = courierProfileService.setAvailability(user.getId(), CourierAvailability.ONLINE_AVAILABLE);
        return ResponseEntity.ok(ApiResponse.success("Disponibilidade definida para ONLINE", resp));
    }

    @PostMapping("/availability/offline")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CourierProfileResponse>> setOffline() {
        User user = getCurrentUser();
        CourierProfileResponse resp = courierProfileService.setAvailability(user.getId(), CourierAvailability.OFFLINE);
        return ResponseEntity.ok(ApiResponse.success("Disponibilidade definida para OFFLINE", resp));
    }

    @PostMapping("/availability/pause")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CourierProfileResponse>> setPause() {
        User user = getCurrentUser();
        CourierProfileResponse resp = courierProfileService.setAvailability(user.getId(), CourierAvailability.PAUSED);
        return ResponseEntity.ok(ApiResponse.success("Disponibilidade definida para PAUSE", resp));
    }

    @PostMapping("/location")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CourierProfileResponse>> updateLocation(@Valid @RequestBody CourierLocationRequest request) {
        User user = getCurrentUser();
        CourierProfileResponse resp = courierProfileService.updateLocation(user.getId(), request, CourierLocationSource.COURIER_APP);
        return ResponseEntity.ok(ApiResponse.success("Localizacao atualizada", resp));
    }

    @GetMapping("/delivery/invites")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<DeliveryInviteResponse>>> getInvites() {
        User user = getCurrentUser();
        CourierProfile c = courierProfileService.requireByUserId(user.getId());
        List<DeliveryCourierInvite> list = inviteRepository.findByCourier_IdAndStatusOrderByInvitedAtDesc(c.getId(), DeliveryCourierInviteStatus.PENDING);
        List<DeliveryInviteResponse> resp = list.stream().map(this::mapInvite).toList();
        return ResponseEntity.ok(ApiResponse.success("Convites listados", resp));
    }

    @PostMapping("/delivery/invites/{inviteId}/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> acceptInvite(@PathVariable Long inviteId) {
        User user = getCurrentUser();
        CourierProfile c = courierProfileService.requireByUserId(user.getId());
        DeliveryCourierInvite invite = inviteRepository.findByIdAndCourier_Id(inviteId, c.getId())
                .orElseThrow(() -> new BusinessException("DELIVERY_INVITE_NOT_FOUND"));
        
        deliveryJobService.acceptInvite(invite.getTenant().getId(), inviteId, user.getId());
        return ResponseEntity.ok(ApiResponse.success("Convite aceito", null));
    }

    @PostMapping("/delivery/invites/{inviteId}/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> rejectInvite(@PathVariable Long inviteId, @RequestParam(name = "reason", required = false) String reason) {
        User user = getCurrentUser();
        CourierProfile c = courierProfileService.requireByUserId(user.getId());
        DeliveryCourierInvite invite = inviteRepository.findByIdAndCourier_Id(inviteId, c.getId())
                .orElseThrow(() -> new BusinessException("DELIVERY_INVITE_NOT_FOUND"));

        deliveryJobService.rejectInvite(invite.getTenant().getId(), inviteId, user.getId(), reason);
        return ResponseEntity.ok(ApiResponse.success("Convite rejeitado", null));
    }

    @PostMapping("/delivery/jobs/{jobId}/picked-up")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markPickedUp(@PathVariable Long jobId) {
        User user = getCurrentUser();
        DeliveryJob job = deliveryJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("DELIVERY_JOB_NOT_FOUND"));

        deliveryJobService.pickedUp(job.getTenant().getId(), jobId, user.getId());
        return ResponseEntity.ok(ApiResponse.success("Pedido coletado", null));
    }

    @PostMapping("/delivery/jobs/{jobId}/in-transit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markInTransit(@PathVariable Long jobId) {
        User user = getCurrentUser();
        DeliveryJob job = deliveryJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("DELIVERY_JOB_NOT_FOUND"));

        deliveryJobService.inTransit(job.getTenant().getId(), jobId, user.getId());
        return ResponseEntity.ok(ApiResponse.success("Pedido em transito", null));
    }

    @PostMapping("/delivery/jobs/{jobId}/delivered")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markDelivered(@PathVariable Long jobId) {
        User user = getCurrentUser();
        DeliveryJob job = deliveryJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("DELIVERY_JOB_NOT_FOUND"));

        deliveryJobService.delivered(job.getTenant().getId(), jobId, user.getId());
        return ResponseEntity.ok(ApiResponse.success("Pedido entregue", null));
    }

    @PostMapping("/delivery/jobs/{jobId}/report-issue")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> reportIssue(@PathVariable Long jobId, @RequestParam(name = "reason") String reason) {
        User user = getCurrentUser();
        DeliveryJob job = deliveryJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("DELIVERY_JOB_NOT_FOUND"));

        deliveryJobService.reportIssue(job.getTenant().getId(), jobId, user.getId(), reason);
        return ResponseEntity.ok(ApiResponse.success("Incidente reportado", null));
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException("DELIVERY_FORBIDDEN");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new BusinessException("DELIVERY_COURIER_NOT_FOUND"));
    }

    private CourierProfileResponse mapProfile(CourierProfile c) {
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

    private DeliveryInviteResponse mapInvite(DeliveryCourierInvite invite) {
        DeliveryJob job = invite.getDeliveryJob();
        return new DeliveryInviteResponse(
                invite.getId(),
                job != null ? job.getId() : null,
                invite.getCourier() != null ? invite.getCourier().getId() : null,
                invite.getStatus(),
                invite.getDistanceToPickupKm(),
                invite.getInvitedAt(),
                invite.getExpiresAt(),
                invite.getRespondedAt(),
                invite.getRejectionReason()
        );
    }
}

