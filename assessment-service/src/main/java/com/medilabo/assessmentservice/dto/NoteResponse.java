package com.medilabo.assessmentservice.dto;

import java.time.Instant;

/**
 * Copie locale du contrat exposé par notes-service (élément de GET /notes).
 */
public record NoteResponse(
        String id,
        Long patientId,
        String note,
        Instant createdAt
) {
}