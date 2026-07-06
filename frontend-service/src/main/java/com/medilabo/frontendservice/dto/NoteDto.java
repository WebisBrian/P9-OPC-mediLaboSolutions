package com.medilabo.frontendservice.dto;

import java.time.Instant;

public record NoteDto(
        String id,
        Long patientId,
        String note,
        Instant createdAt
) {
}