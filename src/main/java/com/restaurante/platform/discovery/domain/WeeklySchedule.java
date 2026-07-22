package com.restaurante.platform.discovery.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

public record WeeklySchedule(Set<DayOfWeek> openDays, LocalTime opensAt, LocalTime closesAt) {

    public WeeklySchedule {
        openDays = Set.copyOf(openDays);
    }
}
