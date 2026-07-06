package com.medilabo.notesservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NoteRequest {

    @NotNull
    private Long patientId;

    // Texte libre non structuré : pas de format imposé, mais borne haute pour éviter les documents surdimensionnés.
    @NotBlank
    @Size(max = 5000)
    private String note;
}