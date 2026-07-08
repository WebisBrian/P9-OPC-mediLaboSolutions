package com.medilabo.assessmentservice.dto;

import java.util.List;

public record AssessmentResponse(
        Long patientId,
        String riskLevel,
        List<String> triggers
) {
}