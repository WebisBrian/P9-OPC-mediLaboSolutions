package com.medilabo.frontendservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// Ne mappe que ce dont le front a besoin, pas l'objet Spring Page complet (ex: pageable, sort).
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotePage(
        List<NoteDto> content,
        int number,
        int totalPages,
        boolean first,
        boolean last
) {
}