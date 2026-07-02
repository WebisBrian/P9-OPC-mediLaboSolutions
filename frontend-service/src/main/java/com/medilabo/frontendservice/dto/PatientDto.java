package com.medilabo.frontendservice.dto;

import java.time.LocalDate;

public record PatientDto(
        Long id,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String gender,
        String address,
        String phone
) {
}