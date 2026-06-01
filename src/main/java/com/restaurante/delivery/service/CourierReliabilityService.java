package com.restaurante.delivery.service;

import com.restaurante.delivery.repository.CourierPenaltyEventRepository;
import com.restaurante.delivery.repository.CourierProfileRepository;
import com.restaurante.delivery.repository.CourierReliabilityProfileRepository;
import com.restaurante.model.entity.CourierPenaltyEvent;
import com.restaurante.model.entity.CourierProfile;
import com.restaurante.model.entity.CourierReliabilityProfile;
import com.restaurante.model.enums.*;
import com.restaurante.service.operacional.OperationalEventLogService;
import com.restaurante.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class CourierReliabilityService {

    private final CourierReliabilityProfileRepository profileRepository;
    private final CourierPenaltyEventRepository penaltyEventRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final OperationalEventLogService operationalEventLogService;

    public CourierReliabilityService(CourierReliabilityProfileRepository profileRepository,
                                     CourierPenaltyEventRepository penaltyEventRepository,
                                     CourierProfileRepository courierProfileRepository,
                                     OperationalEventLogService operationalEventLogService) {
        this.profileRepository = profileRepository;
        this.penaltyEventRepository = penaltyEventRepository;
        this.courierProfileRepository = courierProfileRepository;
        this.operationalEventLogService = operationalEventLogService;
    }

    public CourierReliabilityProfile getOrCreateProfile(CourierProfile courier) {
        return profileRepository.findByCourierId(courier.getId())
                .orElseGet(() -> {
                    CourierReliabilityProfile profile = new CourierReliabilityProfile();
                    profile.setCourier(courier);
                    profile.setScore(100);
                    profile.setLevel(CourierReliabilityLevel.EXCELLENT);
                    return profileRepository.save(profile);
                });
    }

    public CourierReliabilityProfile applyPenalty(Long courierId, CourierPenaltyType penaltyType,
                                                Long deliveryJobId, Long inviteId, String reason) {
        CourierProfile courier = courierProfileRepository.findById(courierId)
                .orElseThrow(() -> new BusinessException("COURIER_PROFILE_NOT_FOUND"));

        CourierReliabilityProfile profile = getOrCreateProfile(courier);

        int delta = getPointsDelta(penaltyType);
        CourierPenaltySeverity severity = getSeverity(penaltyType);

        // Save Penalty Event
        CourierPenaltyEvent event = new CourierPenaltyEvent();
        event.setCourier(courier);
        event.setPenaltyType(penaltyType);
        event.setSeverity(severity);
        event.setPointsDelta(delta);
        event.setReason(reason);
        event.setAppliedAt(LocalDateTime.now());
        event.setStatus(CourierPenaltyStatus.ACTIVE);
        penaltyEventRepository.save(event);

        // Update statistics
        incrementStats(profile, penaltyType);

        // Recalculate score
        int oldScore = profile.getScore();
        int newScore = Math.max(0, Math.min(100, oldScore + delta));
        profile.setScore(newScore);
        profile.setLastPenaltyAt(LocalDateTime.now());

        // Update Level
        profile.setLevel(determineLevel(newScore));

        // Evaluate automated pauses and suspensions
        evaluateRulesAndSuspensions(courier, profile);

        profileRepository.save(profile);
        courierProfileRepository.save(courier);

        // Log events
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("courierId", courierId);
        metadata.put("penaltyType", penaltyType.name());
        metadata.put("pointsDelta", delta);
        metadata.put("newScore", newScore);
        metadata.put("reason", reason);

        Long tenantId = courier.getTenant() != null ? courier.getTenant().getId() : 1L; // fallback to 1 if null
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.COURIER_PENALTY_APPLIED,
                OperationalEntityType.COURIER_RELIABILITY_PROFILE,
                profile.getId(),
                OperationalOrigem.SYSTEM,
                reason,
                metadata,
                "127.0.0.1",
                "Consuma Log Engine"
        );

        return profile;
    }

    public CourierReliabilityProfile rewardSuccessfulDelivery(Long courierId) {
        CourierProfile courier = courierProfileRepository.findById(courierId)
                .orElseThrow(() -> new BusinessException("COURIER_PROFILE_NOT_FOUND"));

        CourierReliabilityProfile profile = getOrCreateProfile(courier);

        // Increment successfully completed deliveries
        profile.setCompletedDeliveries(profile.getCompletedDeliveries() + 1);

        // score reward
        int oldScore = profile.getScore();
        int newScore = Math.min(100, oldScore + 1);
        profile.setScore(newScore);

        profile.setLevel(determineLevel(newScore));

        // Auto reactivate if score gets better? Normally suspension has to wait out or be manual,
        // but if suspended we check if suspension expired. Let's do checkSuspension here too.
        checkSuspensionStatus(courier, profile);

        profileRepository.save(profile);
        courierProfileRepository.save(courier);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("courierId", courierId);
        metadata.put("newScore", newScore);

        Long tenantId = courier.getTenant() != null ? courier.getTenant().getId() : 1L;
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.COURIER_SCORE_UPDATED,
                OperationalEntityType.COURIER_RELIABILITY_PROFILE,
                profile.getId(),
                OperationalOrigem.SYSTEM,
                "Reward for successful delivery",
                metadata,
                "127.0.0.1",
                "Consuma Log Engine"
        );

        return profile;
    }

    public void checkAllSuspensions() {
        List<CourierReliabilityProfile> allProfiles = profileRepository.findAll();
        for (CourierReliabilityProfile profile : allProfiles) {
            CourierProfile courier = profile.getCourier();
            checkSuspensionStatus(courier, profile);
        }
    }

    private void checkSuspensionStatus(CourierProfile courier, CourierReliabilityProfile profile) {
        if (profile.getSuspensionUntil() != null && profile.getSuspensionUntil().isBefore(LocalDateTime.now())) {
            profile.setSuspensionUntil(null);
            if (courier.getStatus() == CourierStatus.SUSPENDED) {
                courier.setStatus(CourierStatus.ACTIVE);
                courierProfileRepository.save(courier);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("courierId", courier.getId());

                Long tenantId = courier.getTenant() != null ? courier.getTenant().getId() : 1L;
                operationalEventLogService.logGenericForTenant(
                        tenantId,
                        OperationalEventType.COURIER_REACTIVATED,
                        OperationalEntityType.COURIER_PROFILE,
                        courier.getId(),
                        OperationalOrigem.SYSTEM,
                        "Suspension ended naturally",
                        metadata,
                        "127.0.0.1",
                        "Consuma Log Engine"
                );
            }
        }
    }

    private int getPointsDelta(CourierPenaltyType type) {
        return switch (type) {
            case INVITE_REJECTED -> -1;
            case INVITE_MISSED, INVITE_EXPIRED -> -2;
            case LATE_PICKUP -> -3;
            case NO_SHOW -> -15;
            case CANCEL_AFTER_ACCEPT -> -20;
            case DELIVERY_FAILED_BY_COURIER -> -25;
            case MANUAL_PENALTY -> -5;
            case MANUAL_BONUS -> 5;
        };
    }

    private CourierPenaltySeverity getSeverity(CourierPenaltyType type) {
        return switch (type) {
            case INVITE_REJECTED -> CourierPenaltySeverity.LOW;
            case INVITE_MISSED, INVITE_EXPIRED, LATE_PICKUP -> CourierPenaltySeverity.MEDIUM;
            case NO_SHOW, CANCEL_AFTER_ACCEPT -> CourierPenaltySeverity.HIGH;
            case DELIVERY_FAILED_BY_COURIER -> CourierPenaltySeverity.CRITICAL;
            case MANUAL_PENALTY, MANUAL_BONUS -> CourierPenaltySeverity.MEDIUM;
        };
    }

    private CourierReliabilityLevel determineLevel(int score) {
        if (score >= 90) return CourierReliabilityLevel.EXCELLENT;
        if (score >= 75) return CourierReliabilityLevel.NORMAL;
        if (score >= 60) return CourierReliabilityLevel.LOW_PRIORITY;
        if (score >= 40) return CourierReliabilityLevel.WARNED;
        return CourierReliabilityLevel.SUSPENDED;
    }

    private void incrementStats(CourierReliabilityProfile profile, CourierPenaltyType type) {
        switch (type) {
            case INVITE_REJECTED -> profile.setRejectedInvites(profile.getRejectedInvites() + 1);
            case INVITE_MISSED -> profile.setMissedInvites(profile.getMissedInvites() + 1);
            case INVITE_EXPIRED -> profile.setExpiredInvites(profile.getExpiredInvites() + 1);
            case CANCEL_AFTER_ACCEPT -> profile.setCancelledAfterAcceptCount(profile.getCancelledAfterAcceptCount() + 1);
            case NO_SHOW -> profile.setNoShowCount(profile.getNoShowCount() + 1);
            case DELIVERY_FAILED_BY_COURIER -> profile.setFailedDeliveryCount(profile.getFailedDeliveryCount() + 1);
            default -> {}
        }
        profile.setTotalInvites(profile.getTotalInvites() + 1);
    }

    private void evaluateRulesAndSuspensions(CourierProfile courier, CourierReliabilityProfile profile) {
        LocalDateTime now = LocalDateTime.now();

        // 1. Missed/Expired in last 24h
        LocalDateTime oneDayAgo = now.minusDays(1);
        List<CourierPenaltyEvent> last24hPenalties = penaltyEventRepository
                .findByCourierIdAndAppliedAtAfter(courier.getId(), oneDayAgo);
        
        long missed24h = last24hPenalties.stream()
                .filter(p -> p.getStatus() == CourierPenaltyStatus.ACTIVE &&
                        (p.getPenaltyType() == CourierPenaltyType.INVITE_MISSED || 
                         p.getPenaltyType() == CourierPenaltyType.INVITE_EXPIRED))
                .count();

        if (missed24h >= 3) {
            profile.setSuspensionUntil(now.plusMinutes(30));
            suspendCourier(courier, "Automatic pause: 3 missed/expired invites in 24h");
            return;
        }

        // 2. Missed/Expired in last 7 days
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        List<CourierPenaltyEvent> last7dPenalties = penaltyEventRepository
                .findByCourierIdAndAppliedAtAfter(courier.getId(), sevenDaysAgo);

        long missed7d = last7dPenalties.stream()
                .filter(p -> p.getStatus() == CourierPenaltyStatus.ACTIVE &&
                        (p.getPenaltyType() == CourierPenaltyType.INVITE_MISSED || 
                         p.getPenaltyType() == CourierPenaltyType.INVITE_EXPIRED))
                .count();

        if (missed7d >= 10) {
            profile.setSuspensionUntil(now.plusHours(24));
            suspendCourier(courier, "Automatic 24h suspension: 10 missed/expired invites in 7d");
            return;
        }

        // 3. No Show in last 7 days
        long noShows7d = last7dPenalties.stream()
                .filter(p -> p.getStatus() == CourierPenaltyStatus.ACTIVE && p.getPenaltyType() == CourierPenaltyType.NO_SHOW)
                .count();

        if (noShows7d >= 2) {
            profile.setSuspensionUntil(now.plusHours(48));
            suspendCourier(courier, "Automatic 48h suspension: 2 no-shows in 7d");
            return;
        }

        // 4. Score < 40
        if (profile.getScore() < 40) {
            profile.setLevel(CourierReliabilityLevel.SUSPENDED);
            profile.setSuspensionUntil(now.plusDays(7));
            suspendCourier(courier, "Automatic 7d suspension: score dropped below 40");
            return;
        }
    }

    private void suspendCourier(CourierProfile courier, String reason) {
        courier.setStatus(CourierStatus.SUSPENDED);
        courier.setCurrentAvailability(CourierAvailability.OFFLINE);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("courierId", courier.getId());
        metadata.put("reason", reason);

        Long tenantId = courier.getTenant() != null ? courier.getTenant().getId() : 1L;
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.COURIER_SUSPENDED,
                OperationalEntityType.COURIER_PROFILE,
                courier.getId(),
                OperationalOrigem.SYSTEM,
                reason,
                metadata,
                "127.0.0.1",
                "Consuma Log Engine"
        );
    }
}
