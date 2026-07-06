package com.medilabo.notesservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    // Texte libre non structuré : pas de borne de taille ni de format, contrairement aux champs patient.
    @NotBlank
    private String note;
}