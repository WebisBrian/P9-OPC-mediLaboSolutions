package com.medilabo.frontendservice.dto;

import java.util.List;

public record AssessmentDto(
        Long patientId,
        String riskLevel,
        List<String> triggers
) {
}