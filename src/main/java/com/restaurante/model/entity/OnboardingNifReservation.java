package com.restaurante.model.entity;

import com.restaurante.model.enums.OnboardingNifReservationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "onboarding_nif_reservations")
public class OnboardingNifReservation extends BaseEntity {
    @Column(name = "normalized_nif", nullable = false, length = 30) private String normalizedNif;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "onboarding_request_id", nullable = false) private OnboardingRequest onboardingRequest;
    @Enumerated(EnumType.STRING) @Column(name = "state", nullable = false, length = 20) private OnboardingNifReservationState state;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "business_account_id") private BusinessAccount businessAccount;

    public String getNormalizedNif() { return normalizedNif; }
    public void setNormalizedNif(String v) { normalizedNif = v; }
    public OnboardingRequest getOnboardingRequest() { return onboardingRequest; }
    public void setOnboardingRequest(OnboardingRequest v) { onboardingRequest = v; }
    public OnboardingNifReservationState getState() { return state; }
    public void setState(OnboardingNifReservationState v) { state = v; }
    public BusinessAccount getBusinessAccount() { return businessAccount; }
    public void setBusinessAccount(BusinessAccount v) { businessAccount = v; }
}
