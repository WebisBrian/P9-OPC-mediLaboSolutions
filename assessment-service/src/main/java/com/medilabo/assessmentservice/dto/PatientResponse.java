package com.medilabo.assessmentservice.dto;

import java.time.LocalDate;

/**
 * Copie locale du contrat exposé par patient-service (GET /patients/{id}).
 * Pas de dépendance partagée entre microservices : ce record ne mappe que ce
 * dont assessment-service a besoin. gender reste une String (pas l'enum Gender
 * de patient-service) pour éviter tout couplage à un type interne d'un autre service.
 */
public record PatientResponse(
        Long id,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String gender,
        String address,
        String phone
) {
}