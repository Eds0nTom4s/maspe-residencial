package com.restaurante.repository;

import com.restaurante.model.entity.OnboardingNifReservation;
import com.restaurante.model.enums.OnboardingNifReservationState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OnboardingNifReservationRepository extends JpaRepository<OnboardingNifReservation, Long> {
    Optional<OnboardingNifReservation> findByNormalizedNifAndState(
            String normalizedNif, OnboardingNifReservationState state);
    Optional<OnboardingNifReservation> findByOnboardingRequestIdAndState(
            Long onboardingRequestId, OnboardingNifReservationState state);
}
