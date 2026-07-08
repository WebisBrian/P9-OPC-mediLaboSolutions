package com.medilabo.assessmentservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// Ne mappe que ce dont assessment-service a besoin, pas l'objet Spring Page complet.
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotePage(
        List<NoteResponse> content,
        int number,
        int totalPages,
        boolean first,
        boolean last
) {
}